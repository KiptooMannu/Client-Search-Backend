package com.kazikonnect.backend.features.payment;

import com.kazikonnect.backend.features.auth.User;
import com.kazikonnect.backend.features.auth.UserRepository;
import com.kazikonnect.backend.features.auth.UserRole;
import com.kazikonnect.backend.features.common.Notification;
import com.kazikonnect.backend.features.common.NotificationRepository;
import com.kazikonnect.backend.features.wallet.WalletService;
import com.kazikonnect.backend.features.worker.JobRequest;
import com.kazikonnect.backend.features.worker.JobRequestRepository;
import com.kazikonnect.backend.features.worker.JobStatus;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class PaymentService {

    private final JobRequestRepository jobRequestRepository;
    private final UserRepository userRepository;
    private final EscrowPaymentRepository escrowPaymentRepository;
    private final WebhookProcessedLogRepository webhookProcessedLogRepository;
    private final PaymentAuditLogRepository paymentAuditLogRepository;
    private final NotificationRepository notificationRepository;
    private final WalletService walletService;
    private final MpesaService mpesaService;

    @Value("${payment.platform-fee-percent:10.0}")
    private double platformFeePercent;

    @Transactional
    public StkPushResponse initiateStkPush(UUID jobId, String phoneNumber, Principal principal) {
        User actor = getActor(principal);
        JobRequest job = jobRequestRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found."));

        if (actor.getRole() == com.kazikonnect.backend.features.auth.UserRole.CLIENT
                && !job.getClient().getId().equals(actor.getId())) {
            throw new RuntimeException("Forbidden: you are not permitted to pay for this job.");
        }

        Double amount = Optional.ofNullable(job.getTotalCost()).orElse(0.0);
        if (amount <= 0) {
            throw new RuntimeException("Invalid job amount.");
        }

        String normalizedPhone = mpesaService.normalizePhoneNumber(phoneNumber);

        Optional<EscrowPayment> latestPayment = escrowPaymentRepository.findTopByJobRequestIdOrderByCreatedAtDesc(jobId);
        if (latestPayment.isPresent()) {
            EscrowPaymentStatus status = latestPayment.get().getStatus();
            // Only reject if payment is already pending, escrowed or successfully captured
            // FAILED payments CAN be retried by initiating a new STK push
            if (status == EscrowPaymentStatus.PENDING 
                || status == EscrowPaymentStatus.ESCROWED 
                || status == EscrowPaymentStatus.SUCCESS) {
                throw new RuntimeException("A payment is already pending or captured for this job. Please wait for completion before retrying.");
            }
        }

        StkPushResponse pushResponse = mpesaService.initiateStkPush(normalizedPhone, amount, jobId.toString(),
            "M-Pesa payment for job " + jobId);

        if (pushResponse.checkoutRequestId() == null || pushResponse.checkoutRequestId().isBlank()) {
            throw new RuntimeException("Failed to obtain MPESA checkout request id for job " + jobId);
        }

        EscrowPayment payment = EscrowPayment.builder()
                .jobRequest(job)
                .status(EscrowPaymentStatus.PENDING)
                .amount(amount)
                .phoneNumber(normalizedPhone)
                .checkoutRequestId(pushResponse.checkoutRequestId())
                .idempotencyKey(pushResponse.checkoutRequestId())
                .message("STK push sent")
                .timeoutAt(LocalDateTime.now().plusMinutes(15))
                .build();

        escrowPaymentRepository.save(payment);
        saveAuditLog(payment, "STK_PUSH_INITIATED", principal, "STK push initiated", "checkoutRequestId=" + payment.getCheckoutRequestId());
        return new StkPushResponse("PENDING", payment.getCheckoutRequestId(), payment.getMessage());
    }

    @Transactional(readOnly = true)
    public PaymentStatusResponse getPaymentStatus(UUID jobId) {
        Optional<EscrowPayment> paymentOpt = escrowPaymentRepository.findTopByJobRequestIdOrderByCreatedAtDesc(jobId);
        if (paymentOpt.isEmpty()) {
            return new PaymentStatusResponse(
                    "NO_PAYMENT",
                    null,
                    jobId.toString(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    "No payment record found for this job.",
                    null,
                    null,
                    null,
                    null
            );
        }

        EscrowPayment payment = paymentOpt.get();
        // Map internal payment enum to simplified frontend statuses
        String mappedStatus = switch (payment.getStatus()) {
            case PENDING -> "PENDING";
            case SUCCESS, ESCROWED, RELEASED, PARTIALLY_SETTLED -> "PAID";
            case REFUNDED, FAILED -> "FAILED";
            case DISPUTED -> "DISPUTED";
        };

        return new PaymentStatusResponse(
            mappedStatus,
            payment.getId().toString(),
            payment.getJobRequest().getId().toString(),
            payment.getAmount(),
            payment.getPhoneNumber(),
            payment.getCheckoutRequestId(),
            payment.getMpesaReceiptNumber(),
            payment.getPlatformFee(),
            payment.getWorkerAmount(),
            payment.getMessage(),
            formatDateTime(payment.getTransactionDate()),
            formatDateTime(payment.getCreatedAt()),
            formatDateTime(payment.getTimeoutAt()),
            payment.getFailureReason()
        );
    }

    @Transactional(readOnly = true)
    public List<PaymentStatusResponse> getAllEscrowPayments() {
        return escrowPaymentRepository.findAll().stream().map(payment -> new PaymentStatusResponse(
                payment.getStatus().name(),
                payment.getId().toString(),
                payment.getJobRequest().getId().toString(),
                payment.getAmount(),
                payment.getPhoneNumber(),
                payment.getCheckoutRequestId(),
                payment.getMpesaReceiptNumber(),
                payment.getPlatformFee(),
                payment.getWorkerAmount(),
                payment.getMessage(),
                formatDateTime(payment.getTransactionDate()),
                formatDateTime(payment.getCreatedAt()),
                formatDateTime(payment.getTimeoutAt()),
                payment.getFailureReason()
        )).collect(Collectors.toList());
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentService.class);

    @Transactional
    public void releaseEscrow(UUID jobId, Principal principal) {
        User actor = getActor(principal);
        JobRequest job = jobRequestRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found."));

        if (actor.getRole() == com.kazikonnect.backend.features.auth.UserRole.CLIENT
                && !job.getClient().getId().equals(actor.getId())) {
            throw new RuntimeException("Forbidden: you are not permitted to approve this payment.");
        }

        EscrowPayment payment = escrowPaymentRepository.findTopByJobRequestIdAndStatusInOrderByCreatedAtDesc(jobId,
                        List.of(EscrowPaymentStatus.SUCCESS, EscrowPaymentStatus.ESCROWED, EscrowPaymentStatus.PENDING))
                .orElseThrow(() -> new RuntimeException("Payment record not found or not ready to release."));

        ensureStatusTransition(payment, EscrowPaymentStatus.RELEASED);

        double fee = calculatePlatformFee(payment.getAmount());
        payment.setPlatformFee(fee);
        payment.setWorkerAmount(payment.getAmount() - fee);
        payment.setStatus(EscrowPaymentStatus.RELEASED);
        payment.setMessage("Payment released to worker. Platform fee: KES " + fee);
        payment.setUpdatedAt(LocalDateTime.now());
        escrowPaymentRepository.save(payment);
        saveAuditLog(payment, "PAYMENT_RELEASED", principal, "Payment released to worker", null);

        if (job.getWorker() == null || job.getWorker().getUser() == null) {
            throw new RuntimeException("Worker account not found for this job.");
        }

        walletService.creditWallet(job.getWorker().getUser(), payment.getWorkerAmount(),
                "Payment release for job " + jobId);
    }

    @Transactional
    public void refundEscrow(UUID jobId, Principal principal) {
        User actor = getActor(principal);
        JobRequest job = jobRequestRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found."));

        if (actor.getRole() == com.kazikonnect.backend.features.auth.UserRole.CLIENT
                && !job.getClient().getId().equals(actor.getId())) {
            throw new RuntimeException("Forbidden: you are not permitted to refund this escrow.");
        }

        EscrowPayment payment = escrowPaymentRepository.findTopByJobRequestIdAndStatusInOrderByCreatedAtDesc(jobId,
                        List.of(EscrowPaymentStatus.PENDING, EscrowPaymentStatus.SUCCESS, EscrowPaymentStatus.ESCROWED))
                .orElseThrow(() -> new RuntimeException("Payment record not found or not eligible for refund."));

        if (payment.getStatus() != EscrowPaymentStatus.SUCCESS
                && payment.getStatus() != EscrowPaymentStatus.PENDING
                && payment.getStatus() != EscrowPaymentStatus.ESCROWED) {
            throw new RuntimeException("Only pending or captured payments can be refunded.");
        }

        payment.setStatus(EscrowPaymentStatus.REFUNDED);
        payment.setMessage("Payment refunded to payer.");
        payment.setUpdatedAt(LocalDateTime.now());
        escrowPaymentRepository.save(payment);
        saveAuditLog(payment, "PAYMENT_REFUNDED", principal, "Refund requested by actor", null);

        // Credit client's wallet with refund amount
        if (job.getClient() != null && payment.getAmount() > 0) {
            walletService.creditWallet(job.getClient(), payment.getAmount(),
                    "Escrow refund for job " + jobId);
        }
    }

    @Transactional
    public void refundEscrowBySystem(UUID jobId, String reason) {
        Optional<EscrowPayment> paymentOpt = escrowPaymentRepository.findTopByJobRequestIdAndStatusInOrderByCreatedAtDesc(jobId,
                        List.of(EscrowPaymentStatus.PENDING, EscrowPaymentStatus.SUCCESS, EscrowPaymentStatus.ESCROWED));
        if (paymentOpt.isEmpty()) {
            return;
        }
        EscrowPayment payment = paymentOpt.get();
        ensureStatusTransition(payment, EscrowPaymentStatus.REFUNDED);
        payment.setStatus(EscrowPaymentStatus.REFUNDED);
        payment.setMessage(reason != null ? reason : "Payment refunded by system.");
        payment.setUpdatedAt(LocalDateTime.now());
        escrowPaymentRepository.save(payment);
        saveAuditLog(payment, "PAYMENT_REFUNDED", null, reason, null);

        // Credit client's wallet
        if (payment.getJobRequest() != null && payment.getJobRequest().getClient() != null && payment.getAmount() > 0) {
            walletService.creditWallet(payment.getJobRequest().getClient(), payment.getAmount(),
                    "Refund for job " + jobId + ": " + (reason != null ? reason : "Payment refunded by system."));
        }
    }

    @Transactional
    public void partialRefundEscrow(UUID jobId, double workerAmount, double clientRefund, String reason) {
        JobRequest job = jobRequestRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found."));

        // Accept SUCCESS, ESCROWED, or even PENDING (admin override for test/pending payments)
        Optional<EscrowPayment> paymentOpt = escrowPaymentRepository
                .findTopByJobRequestIdAndStatusInOrderByCreatedAtDesc(jobId,
                        List.of(EscrowPaymentStatus.SUCCESS, EscrowPaymentStatus.ESCROWED,
                                EscrowPaymentStatus.PENDING));

        double fee = calculatePlatformFee(workerAmount);
        double netWorkerAmount = workerAmount - fee;

        if (paymentOpt.isPresent()) {
            EscrowPayment payment = paymentOpt.get();
            // Only enforce sum check when the payment amount is known
            if (payment.getAmount() != null && payment.getAmount() > 0
                    && Math.abs((workerAmount + clientRefund) - payment.getAmount()) > 1.0) {
                throw new RuntimeException(
                        "Worker amount + client refund must equal the total payment amount ("
                                + payment.getAmount() + ")");
            }
            payment.setPlatformFee(fee);
            payment.setWorkerAmount(netWorkerAmount);
            payment.setStatus(EscrowPaymentStatus.PARTIALLY_SETTLED);
            payment.setMessage("Partial settlement. Worker: KES " + workerAmount
                    + " (fee: KES " + fee + "), Client refund: KES " + clientRefund);
            payment.setUpdatedAt(LocalDateTime.now());
            escrowPaymentRepository.save(payment);
            saveAuditLog(payment, "PAYMENT_PARTIALLY_SETTLED", null, reason,
                    "workerAmount=" + workerAmount + ", clientRefund=" + clientRefund);
        } else {
            LOGGER.warn("partialRefundEscrow: no capturable payment for job {}; crediting wallets directly.", jobId);
        }

        // Credit worker wallet (net of platform fee)
        if (job.getWorker() != null && job.getWorker().getUser() != null && workerAmount > 0) {
            walletService.creditWallet(job.getWorker().getUser(), netWorkerAmount,
                    "Partial dispute payout for job " + jobId);
        }

        // Credit client wallet
        if (job.getClient() != null && clientRefund > 0) {
            walletService.creditWallet(job.getClient(), clientRefund,
                    "Partial dispute refund for job " + jobId);
        }
    }

    private String mapFailureReason(Integer resultCode, String resultDesc) {
        if (resultCode == null) {
            if (resultDesc != null && resultDesc.toLowerCase().contains("pin")) return "WRONG_PIN";
            return resultDesc != null && !resultDesc.isBlank() ? resultDesc.toUpperCase() : "UNKNOWN_ERROR";
        }
        return switch (resultCode) {
            case 1032 -> "USER_CANCELLED";
            case 1031 -> "TIMEOUT";
            case 1001 -> "TIMEOUT";
            case 1, 1037 -> "INSUFFICIENT_FUNDS_OR_WRONG_PIN";
            case 1034 -> "WRONG_PIN";
            case 1030 -> "REQUEST_CANCELLED";
            case 2001 -> "NETWORK_FAILURE";
            default -> "MPESA_ERROR_" + resultCode;
        };
    }

    private String mapFailureMessage(Integer resultCode, String resultDesc) {
        if (resultCode == null) {
            if (resultDesc != null && resultDesc.toLowerCase().contains("pin")) return "Incorrect M-Pesa PIN. Please try again.";
            return resultDesc != null && !resultDesc.isBlank() ? resultDesc : "Payment failed. Please try again.";
        }
        return switch (resultCode) {
            case 1032 -> "Payment cancelled by user.";
            case 1031 -> "Payment request timed out. Please try again.";
            case 1001 -> "Payment request timed out. Please try again.";
            case 1, 1037 -> "Insufficient funds in your M-Pesa account or incorrect PIN. Please check and try again.";
            case 1034 -> "Incorrect M-Pesa PIN. Please try again.";
            case 1030 -> "STK request cancelled.";
            case 2001 -> "Unable to communicate with M-Pesa. Please try again.";
            default -> resultDesc != null && !resultDesc.isBlank() ? resultDesc : "Payment failed. Please try again.";
        };
    }

    @Transactional
    public void handleMpesaCallback(MpesaCallbackRequest callbackRequest, String remoteIp) {
        if (callbackRequest == null || callbackRequest.body() == null || callbackRequest.body().stkCallback() == null) {
            LOGGER.warn("Received empty or invalid MPESA callback payload from {}", remoteIp);
            return;
        }

        MpesaCallbackRequest.StkCallback stkCallback = callbackRequest.body().stkCallback();
        Integer resultCode = stkCallback.resultCode();
        String checkoutRequestId = safeString(stkCallback.checkoutRequestId());
        String resultDesc = safeString(stkCallback.resultDesc());

        if (checkoutRequestId == null || checkoutRequestId.isBlank()) {
            LOGGER.warn("MPESA callback missing CheckoutRequestID from {}", remoteIp);
            return;
        }

        String callbackKey = buildCallbackIdempotencyKey(checkoutRequestId);

        if (!mpesaService.isAcceptedCallbackSource(remoteIp)) {
            LOGGER.warn("Rejected MPESA callback from unauthorized source {} for CheckoutRequestID={}", remoteIp, checkoutRequestId);
            return;
        }

        // Insert-first idempotency: attempt to create a placeholder webhook log. If another
        // process has already inserted a row with the same id, a DataIntegrityViolationException
        // will be thrown and we should treat the callback as duplicate/already-processed.
        try {
            webhookProcessedLogRepository.save(WebhookProcessedLog.builder()
                    .id(callbackKey)
                    .checkoutRequestId(checkoutRequestId)
                    .payload(null)
                    .build());
        } catch (DataIntegrityViolationException ex) {
            LOGGER.info("Duplicate MPESA callback ignored (insert conflict) for CheckoutRequestID={}", checkoutRequestId);
            return;
        }

        EscrowPayment payment = escrowPaymentRepository.findByCheckoutRequestIdForUpdate(checkoutRequestId)
                .orElse(null);

        String phoneNumber = null;
        String receiptNumber = null;
        Double amount = null;
        LocalDateTime transactionDate = null;

        MpesaCallbackRequest.CallbackMetadata callbackMetadata = stkCallback.callbackMetadata();
        if (callbackMetadata != null && callbackMetadata.items() != null) {
            for (MpesaCallbackRequest.Item item : callbackMetadata.items()) {
                if (item == null || item.name() == null) {
                    continue;
                }
                switch (item.name()) {
                    case "MpesaReceiptNumber" -> receiptNumber = safeString(item.value());
                    case "Amount" -> amount = toDouble(item.value());
                    case "PhoneNumber" -> phoneNumber = safeString(item.value());
                    case "TransactionDate" -> transactionDate = parseTransactionDate(item.value());
                    default -> {}
                }
            }
        } else {
            LOGGER.warn("MPESA callback metadata missing for CheckoutRequestID={}", checkoutRequestId);
        }

        if (payment == null) {
            LOGGER.warn("No local payment record found for MPESA callback CheckoutRequestID={}", checkoutRequestId);
            webhookProcessedLogRepository.save(WebhookProcessedLog.builder()
                    .id(callbackKey)
                    .checkoutRequestId(checkoutRequestId)
                    .resultCode(resultCode)
                    .resultDescription(resultDesc)
                    .payload(String.valueOf(callbackRequest))
                    .build());
            return;
        }

        if (payment.getStatus() == EscrowPaymentStatus.RELEASED
                || payment.getStatus() == EscrowPaymentStatus.REFUNDED) {
            LOGGER.info("MPESA callback ignored because payment is already terminal for CheckoutRequestID={}", checkoutRequestId);
            webhookProcessedLogRepository.save(WebhookProcessedLog.builder()
                    .id(callbackKey)
                    .checkoutRequestId(checkoutRequestId)
                    .resultCode(resultCode)
                    .resultDescription(resultDesc)
                    .payload(String.valueOf(callbackRequest))
                    .build());
            return;
        }

        payment.setPhoneNumber(phoneNumber != null ? phoneNumber : payment.getPhoneNumber());
        payment.setMpesaReceiptNumber(receiptNumber);
        payment.setAmount(amount != null ? amount : payment.getAmount());
        payment.setTransactionDate(transactionDate);

        boolean success = resultCode != null && resultCode == 0;
        if (success) {
            payment.setStatus(EscrowPaymentStatus.SUCCESS);
            payment.setMessage("Payment captured successfully.");
            payment.setFailureReason(null);

            // Transition job status to ASSIGNED upon successful payment capture
            JobRequest job = payment.getJobRequest();
            if (job != null && (job.getStatus() == JobStatus.ACCEPTED || job.getStatus() == JobStatus.PENDING)) {
                job.setStatus(JobStatus.IN_PROGRESS);
                jobRequestRepository.save(job);
            }
        } else {
            payment.setStatus(EscrowPaymentStatus.FAILED);
            payment.setFailureReason(mapFailureReason(resultCode, resultDesc));
            payment.setMessage(mapFailureMessage(resultCode, resultDesc));
        }
        payment.setUpdatedAt(LocalDateTime.now());
        escrowPaymentRepository.save(payment);
        saveAuditLog(payment, "MPESA_CALLBACK", null,
                success ? "Payment captured successfully" : payment.getFailureReason(),
                String.valueOf(callbackRequest));

        webhookProcessedLogRepository.save(WebhookProcessedLog.builder()
                .id(callbackKey)
                .checkoutRequestId(checkoutRequestId)
                .resultCode(resultCode)
                .resultDescription(resultDesc)
                .payload(String.valueOf(callbackRequest))
                .build());

        LOGGER.info("MPESA callback processed for CheckoutRequestID={} with ResultCode={} and status={}", checkoutRequestId, resultCode, payment.getStatus());
    }

    @Scheduled(cron = "0 */5 * * * *")
    @Transactional
    public void refundExpiredPendingPayments() {
        List<EscrowPayment> expiredPayments = escrowPaymentRepository.findAllByStatusAndTimeoutAtBefore(
                EscrowPaymentStatus.PENDING, LocalDateTime.now());
        for (EscrowPayment payment : expiredPayments) {
            try {
                refundEscrowBySystem(payment.getJobRequest().getId(), "Payment auto-refunded after timeout.");
            } catch (Exception e) {
                LOGGER.error("Failed to auto-refund expired payment record {}", payment.getId(), e);
            }
        }
    }

    private void saveAuditLog(EscrowPayment payment, String eventType, Principal principal, String reason, String payload) {
        if (payment == null) {
            return;
        }
        paymentAuditLogRepository.save(PaymentAuditLog.builder()
                .escrowPayment(payment)
                .eventType(eventType)
                .actor(principal != null ? principal.getName() : null)
                .reason(reason)
                .payload(payload)
                .build());
    }

    private User getActor(Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new RuntimeException("Unauthorized.");
        }
        return userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new RuntimeException("Unauthorized."));
    }

    private void ensureStatusTransition(EscrowPayment payment, EscrowPaymentStatus targetStatus) {
        if (!payment.getStatus().canTransitionTo(targetStatus)) {
            throw new IllegalStateException(
                    String.format("Payment may not transition from %s to %s", payment.getStatus(), targetStatus));
        }
    }

    private double calculatePlatformFee(double amount) {
        return Math.round((amount * platformFeePercent / 100.0) * 100.0) / 100.0;
    }

    private String buildCallbackIdempotencyKey(String checkoutRequestId) {
        return checkoutRequestId;
    }

    private String safeString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private LocalDateTime parseTransactionDate(Object value) {
        if (value == null) {
            return null;
        }
        String raw = value instanceof Number number ? String.valueOf(number) : String.valueOf(value);
        if (raw.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(raw, DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        } catch (Exception e) {
            LOGGER.warn("Unable to parse transaction date from MPESA callback value: {}", raw);
            return null;
        }
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? null : value.toString();
    }

    /**
     * Process M-Pesa B2C transaction result (worker payout confirmation)
     * Updates audit log and marks escrow as RELEASED when payment confirmed
     */
    @Transactional
    public void processMpesaB2cResult(String transactionId, String conversationId, String resultCode, String sourceIp) {
        try {
            // Validate result code (0 = success)
            boolean success = "0".equals(resultCode);
            
            LOGGER.info("Processing B2C result: txId={}, conversationId={}, success={}, sourceIp={}", 
                transactionId, conversationId, success, sourceIp);
            
            if (success && transactionId != null && !transactionId.isBlank()) {
                // Find escrow payment by receipt number or conversation ID
                List<EscrowPayment> payments = escrowPaymentRepository.findAll().stream()
                    .filter(p -> p.getMpesaReceiptNumber() != null && 
                           p.getMpesaReceiptNumber().equals(transactionId))
                    .collect(Collectors.toList());
                
                for (EscrowPayment payment : payments) {
                    // Mark escrow as RELEASED since B2C transfer completed
                    payment.setStatus(EscrowPaymentStatus.RELEASED);
                    payment.setUpdatedAt(LocalDateTime.now());
                    escrowPaymentRepository.save(payment);
                    
                    // Update audit log with payout confirmation
                    saveAuditLog(payment, "B2C_PAYOUT_CONFIRMED", null, 
                        "B2C transaction " + transactionId + " completed", 
                        "transactionId=" + transactionId + ", conversationId=" + conversationId);
                    
                    LOGGER.info("B2C payout confirmed and escrow released for job {}", payment.getJobRequest().getId());
                }
            } else {
                LOGGER.warn("B2C result processing failed: resultCode={}, transactionId={}", resultCode, transactionId);
            }
        } catch (Exception e) {
            LOGGER.error("Error in processMpesaB2cResult: {}", e.getMessage(), e);
        }
    }

    /**
     * Process M-Pesa B2C transaction timeout
     * Updates audit log, schedules retry with exponential backoff, and notifies admin for manual intervention
     */
    @Transactional
    public void processMpesaB2cTimeout(String conversationId, String responseCode, String responseDescription, String sourceIp) {
        try {
            LOGGER.error("B2C timeout processing: conversationId={}, code={}, desc={}, sourceIp={}", 
                conversationId, responseCode, responseDescription, sourceIp);
            
            // Log timeout event for manual review
            List<EscrowPayment> payments = escrowPaymentRepository.findAll().stream()
                .filter(p -> p.getCheckoutRequestId() != null && 
                       p.getCheckoutRequestId().contains(conversationId))
                .collect(Collectors.toList());
            
            for (EscrowPayment payment : payments) {
                // Record timeout in audit log for escalation
                saveAuditLog(payment, "B2C_PAYOUT_TIMEOUT", null,
                    "B2C transaction timed out with code " + responseCode,
                    "conversationId=" + conversationId + ", description=" + responseDescription);
                
                LOGGER.warn("B2C timeout recorded for job {} - requires manual review", payment.getJobRequest().getId());
                
                // Implement retry logic with exponential backoff
                implementB2cRetryLogic(payment, conversationId, responseCode, responseDescription);
                
                // Escalate to admin dashboard and send notification
                escalateB2cTimeoutToAdmin(payment, conversationId, responseCode, responseDescription);
            }
            
        } catch (Exception e) {
            LOGGER.error("Error in processMpesaB2cTimeout: {}", e.getMessage(), e);
        }
    }

    /**
     * Implement retry logic with exponential backoff for B2C timeouts
     * Records retry attempts in audit log and marks for scheduled retry
     */
    private void implementB2cRetryLogic(EscrowPayment payment, String conversationId, String responseCode, String responseDescription) {
        try {
            // Count existing timeout attempts from audit log
            List<PaymentAuditLog> timeoutAttempts = paymentAuditLogRepository.findByEscrowPaymentId(payment.getId()).stream()
                .filter(log -> log.getEventType() != null && log.getEventType().contains("B2C_PAYOUT_TIMEOUT"))
                .collect(Collectors.toList());
            
            int retryCount = timeoutAttempts.size();
            int maxRetries = 5;
            
            if (retryCount < maxRetries) {
                // Calculate exponential backoff: 1min, 2min, 4min, 8min, 16min (max 24hrs in production)
                long backoffMinutes = (long) Math.min(Math.pow(2, retryCount), 1440); // 1440 = 24 hours
                
                String retryDetails = "Retry attempt " + (retryCount + 1) + " of " + maxRetries + 
                                     " scheduled in " + backoffMinutes + " minutes";
                
                saveAuditLog(payment, "B2C_PAYOUT_RETRY_SCHEDULED", null,
                    retryDetails,
                    "backoffMinutes=" + backoffMinutes + ", conversationId=" + conversationId);
                
                LOGGER.info("B2C payout retry scheduled for job {} - backoff: {} minutes", 
                    payment.getJobRequest().getId(), backoffMinutes);
            } else {
                // Max retries exceeded - escalate to manual intervention
                saveAuditLog(payment, "B2C_PAYOUT_RETRY_EXHAUSTED", null,
                    "Max retry attempts (" + maxRetries + ") exceeded. Manual intervention required.",
                    "conversationId=" + conversationId + ", lastResponseCode=" + responseCode);
                
                LOGGER.error("B2C payout max retries exhausted for job {} - manual intervention required", 
                    payment.getJobRequest().getId());
            }
        } catch (Exception e) {
            LOGGER.error("Error in B2C retry logic: {}", e.getMessage(), e);
        }
    }

    /**
     * Escalate B2C timeout to admin dashboard
     * Creates admin notification for immediate action and visibility
     */
    private void escalateB2cTimeoutToAdmin(EscrowPayment payment, String conversationId, String responseCode, String responseDescription) {
        try {
            // Find admin user
            User admin = userRepository.findAll().stream()
                .filter(u -> u.getRole() == UserRole.ADMIN)
                .findFirst()
                .orElse(null);
            
            if (admin != null) {
                String jobId = payment.getJobRequest().getId().toString();
                String title = "B2C Payout Timeout - Manual Review Required";
                String message = "Worker payout for job " + jobId + " has timed out (response: " + responseCode + "). " +
                               "Retry attempts are in progress. Please monitor and escalate if needed.";
                
                // Create admin notification
                Notification adminNotification = Notification.builder()
                    .user(admin)
                    .title(title)
                    .message(message)
                    .type("WARNING")
                    .build();
                
                notificationRepository.save(adminNotification);
                
                // Log escalation event
                saveAuditLog(payment, "B2C_PAYOUT_ESCALATED_TO_ADMIN", null,
                    "Admin notification created for timeout escalation",
                    "adminId=" + admin.getId() + ", conversationId=" + conversationId + ", responseCode=" + responseCode);
                
                LOGGER.warn("B2C timeout escalated to admin for job {} - notification sent", 
                    payment.getJobRequest().getId());
            } else {
                LOGGER.error("No admin user found to escalate B2C timeout for job {}", 
                    payment.getJobRequest().getId());
            }
        } catch (Exception e) {
            LOGGER.error("Error escalating B2C timeout to admin: {}", e.getMessage(), e);
        }
    }
}
