package com.kazikonnect.backend.features.payment;

import com.kazikonnect.backend.features.auth.User;
import com.kazikonnect.backend.features.auth.UserRepository;
import com.kazikonnect.backend.features.auth.UserRole;
import com.kazikonnect.backend.features.common.Notification;
import com.kazikonnect.backend.features.common.NotificationRepository;
import com.kazikonnect.backend.features.worker.JobRequest;
import com.kazikonnect.backend.features.wallet.WithdrawalRequest;
import com.kazikonnect.backend.features.wallet.WithdrawalRequestRepository;
import com.kazikonnect.backend.features.wallet.WalletService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * B2C Payout Service - Handles Business-to-Customer (B2C) transactions
 * Responsible for initiating worker payouts through M-Pesa B2C API
 */
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class B2cPayoutService {

    private final MpesaService mpesaService;
    private final EscrowPaymentRepository escrowPaymentRepository;
    private final PaymentAuditLogRepository paymentAuditLogRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final PhoneValidationService phoneValidationService;
    private final WithdrawalRequestRepository withdrawalRequestRepository;
    private final WalletService walletService;

    private static final Logger LOGGER = LoggerFactory.getLogger(B2cPayoutService.class);

    @Value("${payment.b2c-max-retries:5}")
    private int maxRetryAttempts;

    @Value("${payment.b2c-initial-backoff-minutes:1}")
    private long initialBackoffMinutes;

    @Value("${payment.b2c-max-backoff-minutes:1440}")
    private long maxBackoffMinutes;

    @Value("${payment.platform-fee-percent:10.0}")
    private double platformFeePercent;

    /**
     * Initiate B2C payout to worker
     * Validates worker phone, deducts platform fee, and sends funds via M-Pesa B2C
     * 
     * @param payment The escrow payment to release
     * @param worker The worker receiving the payout
     * @return B2C response with conversation ID for tracking
     */
    @Transactional
    public B2cPayoutResponse initiateB2cPayout(EscrowPayment payment, JobRequest job) {
        if (payment == null || payment.getAmount() == null || payment.getAmount() <= 0) {
            throw new IllegalArgumentException("Invalid payment amount for B2C payout");
        }

        if (job == null || job.getWorker() == null || job.getWorker().getUser() == null) {
            throw new IllegalArgumentException("Worker account not found for B2C payout");
        }

        User worker = job.getWorker().getUser();
        String phoneNumber = job.getWorker().getPhoneNumber();
        if (phoneNumber == null || phoneNumber.isBlank()) {
            phoneNumber = payment.getPhoneNumber();
        }

        // Validate phone number
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new IllegalArgumentException("Worker phone number not set in payment. Cannot process B2C payout.");
        }

        if (!phoneValidationService.isValidKenyanNumber(phoneNumber)) {
            throw new IllegalArgumentException("Worker phone number is not a valid Kenyan number");
        }

        // Check if phone is M-Pesa registered (this validates the number format too)
        if (!phoneValidationService.isMpesaActive(phoneNumber)) {
            LOGGER.warn("Worker {} phone {} may not have M-Pesa active", worker.getId(), phoneNumber);
            // Log but don't block - could be just not verified yet
        }

        // Calculate net amount after platform fee
        double platformFee = payment.getPlatformFee() != null ? payment.getPlatformFee() : 0;
        double netAmount = payment.getAmount() - platformFee;

        if (netAmount <= 0) {
            throw new IllegalArgumentException("Net payout amount must be greater than zero");
        }

        try {
            // Normalize phone to M-Pesa format
            String normalizedPhone = mpesaService.normalizePhoneNumber(phoneNumber);

            LOGGER.info("Initiating B2C payout for job {} to worker {} - Amount: KES {}, Net: KES {}", 
                job.getId(), worker.getId(), payment.getAmount(), netAmount);

            // Call M-Pesa B2C API
            B2cPayoutResponse response = mpesaService.initiateB2cPayout(
                normalizedPhone,
                netAmount,
                job.getId().toString(),
                "Worker payout for job " + job.getId()
            );

            // Update payment with B2C tracking info
            payment.setB2cConversationId(response.conversationId());
            payment.setB2cInitiatedAt(LocalDateTime.now());
            payment.setB2cRetryCount(0);
            payment.setB2cNextRetryAt(null);
            payment.setStatus(EscrowPaymentStatus.B2C_INITIATED);
            payment.setMessage("B2C payout initiated - Conversation ID: " + response.conversationId());
            payment.setUpdatedAt(LocalDateTime.now());

            escrowPaymentRepository.save(payment);

            // Log successful B2C initiation
            saveAuditLog(payment, "B2C_PAYOUT_INITIATED", 
                "B2C payout initiated for worker " + worker.getUsername(),
                "conversationId=" + response.conversationId() + ", amount=" + netAmount + 
                ", workerPhone=" + normalizedPhone);

            // Notify worker of pending payout
            notifyWorkerOfPayout(worker, job, netAmount, response.conversationId());

            LOGGER.info("B2C payout initiated successfully - Conversation ID: {}", response.conversationId());
            return response;

        } catch (Exception e) {
            LOGGER.error("Failed to initiate B2C payout for job {}: {}", job.getId(), e.getMessage(), e);
            
            // Mark payment as failed and update message
            payment.setStatus(EscrowPaymentStatus.B2C_FAILED);
            payment.setMessage("B2C payout initiation failed: " + e.getMessage());
            payment.setFailureReason(e.getMessage());
            payment.setUpdatedAt(LocalDateTime.now());
            escrowPaymentRepository.save(payment);

            saveAuditLog(payment, "B2C_PAYOUT_FAILED",
                "B2C payout initiation failed: " + e.getMessage(),
                "exception=" + e.getClass().getSimpleName());

            throw new RuntimeException("B2C payout initiation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Initiate B2C payout for a worker wallet withdrawal request.
     * Validates worker phone and sends the requested amount via M-Pesa B2C.
     */
    @Transactional
    public B2cPayoutResponse initiateWithdrawalPayout(WithdrawalRequest withdrawal) {
        if (withdrawal == null || withdrawal.getAmount() == null || withdrawal.getAmount() <= 0) {
            throw new IllegalArgumentException("Invalid withdrawal request or amount");
        }
        User worker = withdrawal.getUser();
        if (worker == null) {
            throw new IllegalArgumentException("Worker account not found for withdrawal payout");
        }
        String phoneNumber = withdrawal.getPhoneNumber();
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new IllegalArgumentException("Withdrawal phone number not set. Cannot process payout.");
        }
        if (!phoneValidationService.isValidKenyanNumber(phoneNumber)) {
            throw new IllegalArgumentException("Withdrawal phone number is not a valid Kenyan number");
        }

        try {
            String normalizedPhone = mpesaService.normalizePhoneNumber(phoneNumber);
            double amount = withdrawal.getAmount();

            LOGGER.info("Initiating B2C payout for withdrawal {} to worker {} - Amount: KES {}", 
                withdrawal.getId(), worker.getId(), amount);

            B2cPayoutResponse response = mpesaService.initiateB2cPayout(
                normalizedPhone,
                amount,
                withdrawal.getId().toString(),
                "Worker withdrawal: " + withdrawal.getId()
            );

            withdrawal.setB2cConversationId(response.conversationId());
            withdrawal.setB2cInitiatedAt(LocalDateTime.now());
            withdrawal.setB2cRetryCount(0);
            withdrawal.setB2cNextRetryAt(null);
            withdrawal.setStatus("B2C_INITIATED");
            withdrawal.setUpdatedAt(LocalDateTime.now());

            withdrawalRequestRepository.save(withdrawal);

            LOGGER.info("B2C withdrawal payout initiated successfully - Conversation ID: {}", response.conversationId());
            return response;

        } catch (Exception e) {
            LOGGER.error("Failed to initiate B2C withdrawal payout for request {}: {}", withdrawal.getId(), e.getMessage(), e);
            withdrawal.setStatus("FAILED");
            withdrawal.setUpdatedAt(LocalDateTime.now());
            withdrawalRequestRepository.save(withdrawal);
            throw new RuntimeException("B2C withdrawal payout initiation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Process successful B2C result from M-Pesa webhook
     * Marks payment as successfully released
     */
    @Transactional
    public void processB2cSuccess(String transactionId, String conversationId, String mpesaReceiptNumber, String sourceIp) {
        LOGGER.info("Processing B2C success: txId={}, conversationId={}, receipt={}, sourceIp={}", 
            transactionId, conversationId, mpesaReceiptNumber, sourceIp);

        // Find payment by conversation ID
        Optional<EscrowPayment> paymentOpt = escrowPaymentRepository.findByB2cConversationId(conversationId);
        if (paymentOpt.isEmpty()) {
            Optional<WithdrawalRequest> withdrawalOpt = withdrawalRequestRepository.findByB2cConversationId(conversationId);
            if (withdrawalOpt.isPresent()) {
                WithdrawalRequest withdrawal = withdrawalOpt.get();
                if (!"B2C_INITIATED".equals(withdrawal.getStatus()) && !"B2C_PENDING".equals(withdrawal.getStatus())) {
                    LOGGER.warn("B2C result received for withdrawal in unexpected status: {} for conversation {}", 
                        withdrawal.getStatus(), conversationId);
                    return;
                }
                withdrawal.setStatus("APPROVED");
                withdrawal.setB2cTransactionId(transactionId);
                withdrawal.setB2cCompletedAt(LocalDateTime.now());
                withdrawal.setUpdatedAt(LocalDateTime.now());
                withdrawalRequestRepository.save(withdrawal);

                LOGGER.info("B2C withdrawal payout completed successfully - Conversation: {}, TxId: {}", conversationId, transactionId);
                return;
            }
            LOGGER.warn("No payment or withdrawal found for B2C conversation ID: {}", conversationId);
            return;
        }

        EscrowPayment payment = paymentOpt.get();

        // Verify payment is in expected state
        if (payment.getStatus() != EscrowPaymentStatus.B2C_INITIATED && 
            payment.getStatus() != EscrowPaymentStatus.B2C_PENDING) {
            LOGGER.warn("B2C result received for payment in unexpected status: {} for conversation {}", 
                payment.getStatus(), conversationId);
            return;
        }

        // Update payment with B2C completion info
        payment.setStatus(EscrowPaymentStatus.RELEASED);
        payment.setMpesaReceiptNumber(mpesaReceiptNumber);
        payment.setB2cTransactionId(transactionId);
        payment.setB2cCompletedAt(LocalDateTime.now());
        payment.setMessage("B2C payout completed successfully - Receipt: " + mpesaReceiptNumber);
        payment.setUpdatedAt(LocalDateTime.now());

        escrowPaymentRepository.save(payment);

        // Log successful B2C completion
        saveAuditLog(payment, "B2C_PAYOUT_SUCCESS",
            "B2C payout completed successfully",
            "transactionId=" + transactionId + ", receipt=" + mpesaReceiptNumber + 
            ", conversationId=" + conversationId);

        // Notify worker of successful payout
        if (payment.getJobRequest() != null && payment.getJobRequest().getWorker() != null) {
            User worker = payment.getJobRequest().getWorker().getUser();
            notifyWorkerPayoutSuccess(worker, payment);
        }

        LOGGER.info("B2C payout completed successfully - Conversation: {}, Receipt: {}", conversationId, mpesaReceiptNumber);
    }

    /**
     * Process B2C timeout - mark for retry with exponential backoff
     */
    @Transactional
    public void processB2cTimeout(String conversationId, String responseCode, String responseDescription, String sourceIp) {
        LOGGER.error("Processing B2C timeout: conversationId={}, code={}, desc={}, sourceIp={}", 
            conversationId, responseCode, responseDescription, sourceIp);

        Optional<EscrowPayment> paymentOpt = escrowPaymentRepository.findByB2cConversationId(conversationId);
        if (paymentOpt.isEmpty()) {
            Optional<WithdrawalRequest> withdrawalOpt = withdrawalRequestRepository.findByB2cConversationId(conversationId);
            if (withdrawalOpt.isPresent()) {
                WithdrawalRequest withdrawal = withdrawalOpt.get();
                int retryCount = withdrawal.getB2cRetryCount() != null ? withdrawal.getB2cRetryCount() : 0;
                retryCount++;
                long backoffMinutes = calculateBackoff(retryCount);
                if (retryCount >= maxRetryAttempts) {
                    withdrawal.setStatus("FAILED");
                    withdrawal.setUpdatedAt(LocalDateTime.now());
                    withdrawalRequestRepository.save(withdrawal);

                    // Refund worker's wallet balance
                    walletService.creditWallet(withdrawal.getUser(), withdrawal.getAmount(), 
                        "Refund for failed withdrawal " + withdrawal.getId());
                    LOGGER.error("Max retries exceeded for B2C withdrawal payout: {}", conversationId);
                } else {
                    withdrawal.setB2cRetryCount(retryCount);
                    withdrawal.setB2cNextRetryAt(LocalDateTime.now().plusMinutes(backoffMinutes));
                    withdrawal.setStatus("B2C_RETRY_PENDING");
                    withdrawal.setUpdatedAt(LocalDateTime.now());
                    withdrawalRequestRepository.save(withdrawal);
                    LOGGER.info("B2C withdrawal retry scheduled in {} minutes", backoffMinutes);
                }
                return;
            }
            LOGGER.warn("No payment or withdrawal found for B2C timeout conversation ID: {}", conversationId);
            return;
        }

        EscrowPayment payment = paymentOpt.get();

        // Increment retry count
        int retryCount = payment.getB2cRetryCount() != null ? payment.getB2cRetryCount() : 0;
        retryCount++;

        // Calculate exponential backoff
        long backoffMinutes = calculateBackoff(retryCount);

        if (retryCount >= maxRetryAttempts) {
            // Max retries exhausted
            payment.setStatus(EscrowPaymentStatus.B2C_MAX_RETRIES_EXCEEDED);
            payment.setMessage("B2C payout timeout - Max retry attempts (" + maxRetryAttempts + ") exceeded");
            payment.setFailureReason("B2C_TIMEOUT_MAX_RETRIES");
            
            saveAuditLog(payment, "B2C_PAYOUT_RETRY_EXHAUSTED",
                "Max retry attempts exceeded for B2C payout",
                "conversationId=" + conversationId + ", responseCode=" + responseCode);

            // Escalate to admin for manual intervention
            escalateB2cToAdmin(payment, conversationId, responseCode, responseDescription);

        } else {
            // Schedule retry with exponential backoff
            payment.setB2cRetryCount(retryCount);
            payment.setB2cNextRetryAt(LocalDateTime.now().plusMinutes(backoffMinutes));
            payment.setStatus(EscrowPaymentStatus.B2C_RETRY_PENDING);
            payment.setMessage("B2C payout timeout - Retry " + retryCount + " scheduled in " + backoffMinutes + " minutes");

            saveAuditLog(payment, "B2C_PAYOUT_RETRY_SCHEDULED",
                "B2C retry scheduled - Attempt " + retryCount + " of " + maxRetryAttempts,
                "conversationId=" + conversationId + ", backoffMinutes=" + backoffMinutes + 
                ", responseCode=" + responseCode);

            LOGGER.info("B2C payout retry scheduled: conversation={}, retry={}/{}, backoff={} minutes", 
                conversationId, retryCount, maxRetryAttempts, backoffMinutes);
        }

        payment.setUpdatedAt(LocalDateTime.now());
        escrowPaymentRepository.save(payment);
    }

    /**
     * Retry B2C payout for payments that timed out
     * Called by scheduled task for payments ready to retry
     */
    @Transactional
    public void retryB2cPayout(EscrowPayment payment) {
        if (payment == null || payment.getB2cNextRetryAt() == null) {
            return;
        }

        if (LocalDateTime.now().isBefore(payment.getB2cNextRetryAt())) {
            return; // Not ready for retry yet
        }

        try {
            LOGGER.info("Retrying B2C payout for job {} - Attempt {}", 
                payment.getJobRequest().getId(), payment.getB2cRetryCount());

            JobRequest job = payment.getJobRequest();

            double platformFee = payment.getPlatformFee() != null ? payment.getPlatformFee() : 0;
            double netAmount = payment.getAmount() - platformFee;

            String normalizedPhone = mpesaService.normalizePhoneNumber(payment.getPhoneNumber());

            // Attempt B2C payout again
            B2cPayoutResponse response = mpesaService.initiateB2cPayout(
                normalizedPhone,
                netAmount,
                job.getId().toString(),
                "Worker payout retry for job " + job.getId()
            );

            // Update payment with new conversation ID
            payment.setB2cConversationId(response.conversationId());
            payment.setStatus(EscrowPaymentStatus.B2C_INITIATED);
            payment.setMessage("B2C payout retry initiated - New Conversation ID: " + response.conversationId());
            payment.setB2cNextRetryAt(null);
            payment.setUpdatedAt(LocalDateTime.now());

            escrowPaymentRepository.save(payment);

            saveAuditLog(payment, "B2C_PAYOUT_RETRY_EXECUTED",
                "B2C payout retry executed successfully",
                "newConversationId=" + response.conversationId() + 
                ", previousRetryCount=" + payment.getB2cRetryCount());

            LOGGER.info("B2C payout retry executed successfully - New Conversation ID: {}", response.conversationId());

        } catch (Exception e) {
            LOGGER.error("B2C payout retry failed for job {}: {}", 
                payment.getJobRequest().getId(), e.getMessage(), e);

            saveAuditLog(payment, "B2C_PAYOUT_RETRY_FAILED",
                "B2C payout retry failed: " + e.getMessage(),
                "exception=" + e.getClass().getSimpleName());
        }
    }

    /**
     * Retry B2C payout for a withdrawal request that timed out
     */
    @Transactional
    public void retryWithdrawalPayout(WithdrawalRequest withdrawal) {
        if (withdrawal == null || withdrawal.getB2cNextRetryAt() == null) {
            return;
        }

        if (LocalDateTime.now().isBefore(withdrawal.getB2cNextRetryAt())) {
            return; // Not ready for retry yet
        }

        try {
            LOGGER.info("Retrying B2C payout for withdrawal {} - Attempt {}", 
                withdrawal.getId(), withdrawal.getB2cRetryCount());

            String normalizedPhone = mpesaService.normalizePhoneNumber(withdrawal.getPhoneNumber());

            // Attempt B2C payout again
            B2cPayoutResponse response = mpesaService.initiateB2cPayout(
                normalizedPhone,
                withdrawal.getAmount(),
                withdrawal.getId().toString(),
                "Worker withdrawal retry: " + withdrawal.getId()
            );

            withdrawal.setB2cConversationId(response.conversationId());
            withdrawal.setStatus("B2C_INITIATED");
            withdrawal.setB2cNextRetryAt(null);
            withdrawal.setUpdatedAt(LocalDateTime.now());

            withdrawalRequestRepository.save(withdrawal);

            LOGGER.info("B2C withdrawal payout retry executed successfully - New Conversation ID: {}", response.conversationId());

        } catch (Exception e) {
            LOGGER.error("B2C withdrawal payout retry failed for withdrawal {}: {}", 
                withdrawal.getId(), e.getMessage(), e);
        }
    }

    /**
     * Calculate exponential backoff: 1, 2, 4, 8, 16 minutes (max 24 hours)
     */
    private long calculateBackoff(int retryCount) {
        long backoffMinutes = (long) Math.pow(2, retryCount - 1) * initialBackoffMinutes;
        return Math.min(backoffMinutes, 1440); // Cap at 24 hours
    }

    /**
     * Escalate B2C timeout to admin for manual intervention
     */
    private void escalateB2cToAdmin(EscrowPayment payment, String conversationId, String responseCode, String responseDescription) {
        try {
            Optional<User> adminOpt = userRepository.findAll().stream()
                .filter(u -> u.getRole() == UserRole.ADMIN)
                .findFirst();

            if (adminOpt.isEmpty()) {
                LOGGER.warn("No admin user found to escalate B2C timeout");
                return;
            }

            User admin = adminOpt.get();
            String jobId = payment.getJobRequest().getId().toString();
            String title = "⚠️ B2C Payout Max Retries Exceeded";
            String message = "Worker payout for job " + jobId + " has exceeded maximum retry attempts. " +
                           "Response: " + responseCode + " - " + responseDescription + ". " +
                           "Manual intervention required. Amount: KES " + payment.getAmount();

            Notification notification = Notification.builder()
                .user(admin)
                .title(title)
                .message(message)
                .type("ALERT")
                .createdAt(LocalDateTime.now())
                .build();

            notificationRepository.save(notification);

            saveAuditLog(payment, "B2C_ESCALATED_TO_ADMIN",
                "B2C timeout escalated to admin",
                "jobId=" + jobId + ", conversationId=" + conversationId + 
                ", responseCode=" + responseCode);

            LOGGER.error("B2C timeout escalated to admin for job {}", jobId);

        } catch (Exception e) {
            LOGGER.error("Failed to escalate B2C timeout to admin: {}", e.getMessage(), e);
        }
    }

    /**
     * Notify worker that payout is pending
     */
    private void notifyWorkerOfPayout(User worker, JobRequest job, double amount, String conversationId) {
        try {
            String title = "Payout Processing";
            String message = "Your payout of KES " + amount + " for job " + job.getId() + 
                           " is being processed. You'll receive it within 1-5 minutes.";

            Notification notification = Notification.builder()
                .user(worker)
                .title(title)
                .message(message)
                .type("INFO")
                .createdAt(LocalDateTime.now())
                .build();

            notificationRepository.save(notification);

        } catch (Exception e) {
            LOGGER.error("Failed to notify worker of payout: {}", e.getMessage());
        }
    }

    /**
     * Notify worker of successful payout
     */
    private void notifyWorkerPayoutSuccess(User worker, EscrowPayment payment) {
        try {
            String title = "✅ Payout Received!";
            String message = "Your payout of KES " + payment.getAmount() + 
                           " has been successfully received. Receipt: " + payment.getMpesaReceiptNumber();

            Notification notification = Notification.builder()
                .user(worker)
                .title(title)
                .message(message)
                .type("SUCCESS")
                .createdAt(LocalDateTime.now())
                .build();

            notificationRepository.save(notification);

        } catch (Exception e) {
            LOGGER.error("Failed to notify worker of payout success: {}", e.getMessage());
        }
    }

    private void saveAuditLog(EscrowPayment payment, String eventType, String reason, String details) {
        try {
            paymentAuditLogRepository.save(PaymentAuditLog.builder()
                .escrowPayment(payment)
                .eventType(eventType)
                .actor("SYSTEM")
                .reason(reason)
                .payload(details)
                .build());
        } catch (Exception e) {
            LOGGER.error("Failed to save audit log: {}", e.getMessage());
        }
    }
}
