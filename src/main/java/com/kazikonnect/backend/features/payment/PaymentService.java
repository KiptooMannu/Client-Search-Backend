package com.kazikonnect.backend.features.payment;

import com.kazikonnect.backend.features.auth.User;
import com.kazikonnect.backend.features.auth.UserRepository;
import com.kazikonnect.backend.features.auth.UserRole;
import com.kazikonnect.backend.features.wallet.WalletService;
import com.kazikonnect.backend.features.worker.JobRequest;
import com.kazikonnect.backend.features.worker.JobRequestRepository;
import com.kazikonnect.backend.features.worker.JobStatus;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class PaymentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentService.class);

    private final JobRequestRepository jobRequestRepository;
    private final UserRepository userRepository;
    private final EscrowPaymentRepository escrowPaymentRepository;
    private final WebhookProcessedLogRepository webhookProcessedLogRepository;
    private final PaymentAuditLogRepository paymentAuditLogRepository;
    private final WalletService walletService;
    private final MpesaService mpesaService;
    private final PhoneValidationService phoneValidationService;

    @Value("${payment.platform-fee-percent:10.0}")
    private double platformFeePercent;

    @Transactional
    public StkPushResponse initiateStkPush(UUID jobId, String phoneNumber, Principal principal) {
        User actor = getActor(principal);
        JobRequest job = jobRequestRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found."));

        if (actor.getRole() == UserRole.CLIENT
                && !job.getClient().getId().equals(actor.getId())) {
            throw new RuntimeException("Forbidden: you are not permitted to pay for this job.");
        }

        Double amount = Optional.ofNullable(job.getTotalCost()).orElse(0.0);
        if (amount <= 0) {
            throw new RuntimeException("Invalid job amount.");
        }

        String normalizedPhone = mpesaService.normalizePhoneNumber(phoneNumber);
        
        // Validate phone number format and M-Pesa eligibility
        if (!phoneValidationService.isMpesaActive(normalizedPhone)) {
            throw new RuntimeException("Invalid phone number or M-Pesa not enabled for this carrier.");
        }

        // Check for existing payment and handle it properly
        Optional<EscrowPayment> existingPaymentOpt = escrowPaymentRepository
                .findTopByJobRequestIdOrderByCreatedAtDesc(jobId);
        
        // Rate limit check — count real attempts in the last 5 minutes
        long recentAttempts = escrowPaymentRepository.countByPhoneNumberAndCreatedAtAfter(
                normalizedPhone, LocalDateTime.now().minusMinutes(5));
        
        if (existingPaymentOpt.isPresent()) {
            EscrowPayment existingPayment = existingPaymentOpt.get();
            EscrowPaymentStatus status = existingPayment.getStatus();
            
            // ALLOW RETRY for FAILED, REFUNDED, or timed out PENDING payments
            boolean timedOut = status == EscrowPaymentStatus.PENDING
                    && existingPayment.getTimeoutAt() != null
                    && existingPayment.getTimeoutAt().isBefore(LocalDateTime.now());

            if (status == EscrowPaymentStatus.FAILED || status == EscrowPaymentStatus.REFUNDED || timedOut) {
                String prevReason = timedOut ? "STK_TIMEOUT_NO_CALLBACK" : existingPayment.getFailureReason();
                LOGGER.info("Retrying payment for job {} - status={}, previous reason: {}", 
                    jobId, status, prevReason);
                
                // Rate limit check for retries
                if (!phoneValidationService.isValidStkPushAttempt(normalizedPhone, (int) recentAttempts)) {
                    throw new RuntimeException("Too many STK push attempts. Please try again later.");
                }
                
                // Initiate new STK push
                StkPushResponse pushResponse = mpesaService.initiateStkPush(
                    normalizedPhone, amount, jobId.toString(),
                    "M-Pesa payment for job " + jobId
                );
                
                if (pushResponse.checkoutRequestId() == null || pushResponse.checkoutRequestId().isBlank()) {
                    throw new RuntimeException("Failed to obtain MPESA checkout request id for job " + jobId);
                }
                
                // UPDATE existing record instead of creating new one
                existingPayment.setStatus(EscrowPaymentStatus.PENDING);
                existingPayment.setPhoneNumber(normalizedPhone);
                existingPayment.setCheckoutRequestId(pushResponse.checkoutRequestId());
                existingPayment.setIdempotencyKey(pushResponse.checkoutRequestId());
                existingPayment.setMessage("STK push sent (retry)");
                existingPayment.setFailureReason(null);
                existingPayment.setTimeoutAt(LocalDateTime.now().plusMinutes(15));
                existingPayment.setUpdatedAt(LocalDateTime.now());
                existingPayment.setMpesaReceiptNumber(null);
                existingPayment.setTransactionDate(null);
                existingPayment.setAmount(amount);
                existingPayment.setVersion(existingPayment.getVersion() + 1); // Increment version for optimistic locking
                
                EscrowPayment savedPayment = escrowPaymentRepository.save(existingPayment);
                saveAuditLog(savedPayment, "STK_PUSH_RETRY", principal, 
                    "Retry. Previous reason: " + prevReason,
                    "checkoutRequestId=" + savedPayment.getCheckoutRequestId());
                
                return new StkPushResponse("PENDING", savedPayment.getCheckoutRequestId(), 
                    savedPayment.getMessage());
            }
            
            // Non-retryable terminal or in-flight states
            if (status == EscrowPaymentStatus.ESCROWED || status == EscrowPaymentStatus.SUCCESS) {
                throw new RuntimeException("Payment already captured for this job. Approve the work to release funds.");
            }
            if (status == EscrowPaymentStatus.PENDING && !timedOut) {
                // Active, non-expired pending — STK push still in-flight
                throw new RuntimeException("An STK push is already in progress for this job. Please check your phone.");
            }
            
            // RELEASED, PARTIALLY_SETTLED, etc. — log and reject
            LOGGER.warn("Cannot initiate payment for job {} with status: {}", jobId, status);
            throw new RuntimeException("Cannot initiate payment. Current payment status: " + status);
        }
        
        // Rate limit check for new payment
        if (!phoneValidationService.isValidStkPushAttempt(normalizedPhone, (int) recentAttempts)) {
            throw new RuntimeException("Too many STK push attempts. Please try again later.");
        }

        // CREATE NEW payment only if no existing record exists
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
                .version(0L)
                .build();

        EscrowPayment savedPayment = escrowPaymentRepository.save(payment);
        saveAuditLog(savedPayment, "STK_PUSH_INITIATED", principal, "STK push initiated", 
            "checkoutRequestId=" + savedPayment.getCheckoutRequestId());
        
        return new StkPushResponse("PENDING", savedPayment.getCheckoutRequestId(), savedPayment.getMessage());
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
            case REFUNDED -> "REFUNDED";
            case FAILED -> "FAILED";
            case DISPUTED -> "DISPUTED";
            case B2C_INITIATED, B2C_PENDING, B2C_RETRY_PENDING -> "PROCESSING_PAYOUT";
            case B2C_FAILED, B2C_MAX_RETRIES_EXCEEDED -> "PAYOUT_FAILED";
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
                        List.of(EscrowPaymentStatus.SUCCESS, EscrowPaymentStatus.ESCROWED, EscrowPaymentStatus.RELEASED))
                .orElseThrow(() -> new RuntimeException("Payment record not found or not ready to release. Payment must be confirmed first."));

        if (payment.getStatus() == EscrowPaymentStatus.RELEASED) {
            job.setStatus(JobStatus.APPROVED);
            jobRequestRepository.save(job);
            return;
        }

        ensureStatusTransition(payment, EscrowPaymentStatus.RELEASED);

        double fee = calculatePlatformFee(payment.getAmount());
        payment.setPlatformFee(fee);
        payment.setWorkerAmount(payment.getAmount() - fee);
        payment.setStatus(EscrowPaymentStatus.RELEASED);
        payment.setMessage("Payment released to worker. Platform fee: KES " + fee);
        payment.setUpdatedAt(LocalDateTime.now());
        payment.setVersion(payment.getVersion() + 1);

        // ATOMIC: Update payment and credit wallet in same transaction
        escrowPaymentRepository.save(payment);
        
        if (job.getWorker() == null || job.getWorker().getUser() == null) {
            throw new RuntimeException("Worker account not found for this job.");
        }

        // Keep the job approved after the client releases escrow.
        job.setStatus(JobStatus.APPROVED);
        jobRequestRepository.save(job);

        // This credit happens atomically within the same transaction
        walletService.creditWallet(job.getWorker().getUser(), payment.getWorkerAmount(),
                "Payment release for job " + jobId);

        saveAuditLog(payment, "PAYMENT_RELEASED", principal, "Payment released to worker", null);
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

        ensureStatusTransition(payment, EscrowPaymentStatus.REFUNDED);
        payment.setStatus(EscrowPaymentStatus.REFUNDED);
        payment.setMessage("Payment refunded to payer.");
        payment.setUpdatedAt(LocalDateTime.now());
        payment.setVersion(payment.getVersion() + 1);

        // ATOMIC: Update payment and credit wallet in same transaction
        escrowPaymentRepository.save(payment);
        
        // Update job status to CANCELLED (payment was refunded, job won't proceed)
        job.setStatus(JobStatus.CANCELLED);
        jobRequestRepository.save(job);
        
        // Credit client's wallet with refund amount
        if (job.getClient() != null && payment.getAmount() > 0) {
            walletService.creditWallet(job.getClient(), payment.getAmount(),
                    "Escrow refund for job " + jobId);
        }
        
        saveAuditLog(payment, "PAYMENT_REFUNDED", principal, "Refund requested by actor", null);
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
        payment.setVersion(payment.getVersion() + 1);
        escrowPaymentRepository.save(payment);
        saveAuditLog(payment, "PAYMENT_REFUNDED", null, reason, null);

        // Update job status
        JobRequest job = payment.getJobRequest();
        if (job != null) {
            job.setStatus(JobStatus.CANCELLED);
            jobRequestRepository.save(job);
        }

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
            payment.setVersion(payment.getVersion() + 1);
            
            // ATOMIC: Update payment first
            escrowPaymentRepository.save(payment);
            saveAuditLog(payment, "PAYMENT_PARTIALLY_SETTLED", null, reason,
                    "workerAmount=" + workerAmount + ", clientRefund=" + clientRefund);
        } else {
            LOGGER.warn("partialRefundEscrow: no capturable payment for job {}; crediting wallets directly.", jobId);
        }

        // Update job status to COMPLETED (partial settlement finalized)
        job.setStatus(JobStatus.COMPLETED);
        jobRequestRepository.save(job);

        // ATOMIC: Credit both wallets within same transaction
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
            case 1031, 1001 -> "TIMEOUT";
            case 1, 1037 -> "INSUFFICIENT_FUNDS";
            case 1034 -> "WRONG_PIN";
            case 1030 -> "REQUEST_CANCELLED";
            case 2001 -> "NETWORK_FAILURE";
            default -> "MPESA_ERROR_" + resultCode;
        };
    }

    private String mapFailureMessage(Integer resultCode, String resultDesc) {
        if (resultCode == null) {
            if (resultDesc != null && resultDesc.toLowerCase().contains("pin")) 
                return "Incorrect M-Pesa PIN. Please try again.";
            return resultDesc != null && !resultDesc.isBlank() ? resultDesc : "Payment failed. Please try again.";
        }
        return switch (resultCode) {
            case 1032 -> "Payment cancelled by user.";
            case 1031, 1001 -> "Payment request timed out. Please try again.";
            case 1, 1037 -> "Insufficient funds in your M-Pesa account. Please check your balance and try again.";
            case 1034 -> "Incorrect M-Pesa PIN. Please try again.";
            case 1030 -> "STK request cancelled.";
            case 2001 -> "Unable to communicate with M-Pesa. Please try again.";
            default -> resultDesc != null && !resultDesc.isBlank() ? resultDesc : "Payment failed. Please try again.";
        };
    }

    @Transactional
    public void handleMpesaCallback(MpesaCallbackRequest callbackRequest, String remoteIp) {
        // Log full callback for debugging
        LOGGER.info("Received MPESA callback from IP: {}", remoteIp);
        LOGGER.debug("Full callback payload: {}", callbackRequest);
        
        if (callbackRequest == null || callbackRequest.body() == null || callbackRequest.body().stkCallback() == null) {
            LOGGER.warn("Received empty or invalid MPESA callback payload from {}", remoteIp);
            return;
        }

        MpesaCallbackRequest.StkCallback stkCallback = callbackRequest.body().stkCallback();
        Integer resultCode = stkCallback.resultCode();
        String checkoutRequestId = safeString(stkCallback.checkoutRequestId());
        String resultDesc = safeString(stkCallback.resultDesc());

        LOGGER.info("MPESA callback - CheckoutRequestID: {}, ResultCode: {}, ResultDesc: {}", 
            checkoutRequestId, resultCode, resultDesc);

        if (checkoutRequestId == null || checkoutRequestId.isBlank()) {
            LOGGER.warn("MPESA callback missing CheckoutRequestID from {}", remoteIp);
            return;
        }

        String callbackKey = buildCallbackIdempotencyKey(checkoutRequestId);

        if (!"STK_QUERY_RECONCILE".equals(remoteIp) && !mpesaService.isAcceptedCallbackSource(remoteIp)) {
            LOGGER.error("SECURITY: Rejected MPESA callback from unauthorized source {} for CheckoutRequestID={}. " +
                "This payment will be auto-refunded if not corrected. Verify MPESA_CALLBACK_ALLOWED_IPS config.", 
                remoteIp, checkoutRequestId);
            // CRITICAL: Log rejected callback for manual review
            webhookProcessedLogRepository.save(WebhookProcessedLog.builder()
                    .id("REJECTED_" + callbackKey)
                    .checkoutRequestId(checkoutRequestId)
                    .payload("REJECTED from IP: " + remoteIp + " - " + resultCode + ": " + resultDesc)
                    .build());
            return;
        }

        // Insert-first idempotency: prevent duplicate processing
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

        // Use pessimistic locking to prevent race conditions
        EscrowPayment payment = escrowPaymentRepository.findByCheckoutRequestIdForUpdate(checkoutRequestId)
                .orElse(null);

        String phoneNumber = null;
        String receiptNumber = null;
        Double amount = null;
        LocalDateTime transactionDate = null;

        // Enhanced webhook payload validation
        MpesaCallbackRequest.CallbackMetadata callbackMetadata = stkCallback.callbackMetadata();
        if (callbackMetadata != null && callbackMetadata.items() != null && !callbackMetadata.items().isEmpty()) {
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
            LOGGER.info("Parsed metadata - Receipt: {}, Amount: {}, Phone: {}, Date: {}", 
                receiptNumber, amount, phoneNumber, transactionDate);
        } else {
            LOGGER.warn("MPESA callback metadata missing or empty for CheckoutRequestID={}", checkoutRequestId);
        }

        // Validate required fields for successful transactions
        if (resultCode != null && resultCode == 0) {
            if (amount == null || amount <= 0) {
                LOGGER.error("Invalid amount in MPESA callback for CheckoutRequestID={}", checkoutRequestId);
            }
            if (receiptNumber == null || receiptNumber.isBlank()) {
                LOGGER.warn("Missing receipt number in MPESA callback for CheckoutRequestID={}", checkoutRequestId);
            }
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

        // Skip processing if already in terminal state
        if (payment.getStatus() == EscrowPaymentStatus.RELEASED
                || payment.getStatus() == EscrowPaymentStatus.REFUNDED
                || payment.getStatus() == EscrowPaymentStatus.PARTIALLY_SETTLED) {
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

        // Update payment with M-Pesa data
        if (phoneNumber != null && !phoneNumber.isBlank()) {
            payment.setPhoneNumber(mpesaService.normalizePhoneNumber(phoneNumber));
        }
        if (receiptNumber != null) payment.setMpesaReceiptNumber(receiptNumber);
        if (amount != null && amount > 0) payment.setAmount(amount);
        if (transactionDate != null) payment.setTransactionDate(transactionDate);

        // Handle different result codes appropriately
        if (resultCode == null) {
            // No response from M-Pesa
            payment.setStatus(EscrowPaymentStatus.FAILED);
            payment.setFailureReason("NO_RESPONSE_FROM_MPESA");
            payment.setMessage("No response received from M-Pesa. Please try again.");
            LOGGER.error("MPESA callback with null resultCode for CheckoutRequestID={}", checkoutRequestId);
            
        } else if (resultCode == 0) {
            // ✓ SUCCESSFUL PAYMENT - Payment received and validated by M-Pesa
            payment.setStatus(EscrowPaymentStatus.SUCCESS);
            payment.setMessage("✓ Payment successfully captured and held in escrow. Awaiting work completion and client approval to release.");
            payment.setFailureReason(null);
            LOGGER.info("✓✓✓ PAYMENT SUCCESS for CheckoutRequestID={}, Receipt={}, Amount={}. Status->SUCCESS", 
                checkoutRequestId, receiptNumber, amount);

            // Update job status when payment is captured in escrow
            JobRequest job = payment.getJobRequest();
            if (job != null) {
                if (job.getStatus() == JobStatus.PENDING
                        || job.getStatus() == JobStatus.ACCEPTED
                        || job.getStatus() == JobStatus.ASSIGNED) {
                    job.setStatus(JobStatus.IN_PROGRESS);
                    jobRequestRepository.save(job);
                    LOGGER.info("Job {} status -> IN_PROGRESS (payment captured in escrow)", job.getId());
                } else {
                    LOGGER.info("Job {} already has status {}, not transitioning", job.getId(), job.getStatus());
                }
            } else {
                LOGGER.warn("No job associated with payment for CheckoutRequestID={}", checkoutRequestId);
            }
            
        } else if (resultCode == 1032) {
            // ✗ USER CANCELLED the transaction
            payment.setStatus(EscrowPaymentStatus.FAILED);
            payment.setFailureReason("USER_CANCELLED");
            payment.setMessage("✗ Payment cancelled by you. Please retry if needed.");
            LOGGER.warn("✗ PAYMENT CANCELLED by user for CheckoutRequestID={}", checkoutRequestId);
            
        } else if (resultCode == 1037 || resultCode == 1) {
            // ✗ INSUFFICIENT FUNDS
            payment.setStatus(EscrowPaymentStatus.FAILED);
            payment.setFailureReason("INSUFFICIENT_FUNDS");
            payment.setMessage("✗ Insufficient M-Pesa balance. Please top up your account and retry.");
            LOGGER.warn("✗ PAYMENT FAILED - Insufficient funds (code {}) for CheckoutRequestID={}", resultCode, checkoutRequestId);
            
        } else if (resultCode == 1031 || resultCode == 1001) {
            // ✗ TIMEOUT - User didn't enter PIN in time
            payment.setStatus(EscrowPaymentStatus.FAILED);
            payment.setFailureReason("TIMEOUT");
            payment.setMessage("✗ Payment request timed out. Please try again.");
            LOGGER.warn("✗ PAYMENT FAILED - Timeout for CheckoutRequestID={}", checkoutRequestId);
            
        } else if (resultCode == 1034) {
            // ✗ WRONG PIN
            payment.setStatus(EscrowPaymentStatus.FAILED);
            payment.setFailureReason("WRONG_PIN");
            payment.setMessage("✗ Incorrect M-Pesa PIN. Please try again.");
            LOGGER.warn("✗ PAYMENT FAILED - Wrong PIN for CheckoutRequestID={}", checkoutRequestId);
            
        } else if (resultCode == 1030) {
            // ✗ REQUEST CANCELLED
            payment.setStatus(EscrowPaymentStatus.FAILED);
            payment.setFailureReason("REQUEST_CANCELLED");
            payment.setMessage("✗ Payment request was cancelled. Please retry.");
            LOGGER.warn("✗ PAYMENT CANCELLED for CheckoutRequestID={}", checkoutRequestId);
            
        } else if (resultCode == 2001) {
            // ✗ NETWORK FAILURE
            payment.setStatus(EscrowPaymentStatus.FAILED);
            payment.setFailureReason("NETWORK_FAILURE");
            payment.setMessage("✗ Network failure connecting to M-Pesa. Please try again later.");
            LOGGER.error("✗ PAYMENT FAILED - Network failure for CheckoutRequestID={}", checkoutRequestId);
            
        } else {
            // ✗ OTHER UNKNOWN ERROR
            payment.setStatus(EscrowPaymentStatus.FAILED);
            payment.setFailureReason(mapFailureReason(resultCode, resultDesc));
            payment.setMessage(mapFailureMessage(resultCode, resultDesc));
            LOGGER.error("✗ PAYMENT FAILED - Unknown error code {} ({}) for CheckoutRequestID={}", 
                resultCode, resultDesc, checkoutRequestId);
        }
        
        payment.setUpdatedAt(LocalDateTime.now());
        payment.setVersion(payment.getVersion() + 1);

        // ATOMIC: Update payment and log in same transaction
        EscrowPayment savedPayment = escrowPaymentRepository.save(payment);
        saveAuditLog(savedPayment, "MPESA_CALLBACK", null,
                resultCode != null && resultCode == 0 ? "Payment captured successfully" : 
                (resultCode == 1032 ? "User cancelled payment" :
                 (resultCode == 1037 || resultCode == 1 ? "Insufficient funds" :
                  (resultCode == 1031 || resultCode == 1001 ? "Payment timeout" :
                   (resultCode == 1034 ? "Wrong PIN" : payment.getFailureReason())))),
                String.format("resultCode=%d, resultDesc=%s, receiptNumber=%s", 
                    resultCode, resultDesc, receiptNumber));

        webhookProcessedLogRepository.findById(callbackKey).ifPresentOrElse(
                log -> {
                    log.setResultCode(resultCode);
                    log.setResultDescription(resultDesc);
                    log.setPayload(String.valueOf(callbackRequest));
                    webhookProcessedLogRepository.save(log);
                },
                () -> webhookProcessedLogRepository.save(WebhookProcessedLog.builder()
                        .id(callbackKey)
                        .checkoutRequestId(checkoutRequestId)
                        .resultCode(resultCode)
                        .resultDescription(resultDesc)
                        .payload(String.valueOf(callbackRequest))
                        .build())
        );

        LOGGER.info("MPESA callback processed for CheckoutRequestID={} with ResultCode={} and final status={}",
            checkoutRequestId, resultCode, savedPayment.getStatus());
    }

    /**
     * Reconcile PENDING payments by querying Safaricom STK status API (fallback when callback is missed).
     */
    @Transactional
    public void reconcilePendingStkPayments() {
        LocalDateTime now = LocalDateTime.now();
        List<EscrowPayment> pendingPayments = escrowPaymentRepository.findByStatus(EscrowPaymentStatus.PENDING);

        for (EscrowPayment payment : pendingPayments) {
            if (payment.getCheckoutRequestId() == null || payment.getCheckoutRequestId().isBlank()) {
                continue;
            }
            if (payment.getTimeoutAt() != null && payment.getTimeoutAt().isAfter(now.minusMinutes(1))) {
                continue;
            }
            try {
                Map<String, Object> queryResult = mpesaService.queryStkPushStatus(payment.getCheckoutRequestId());
                Object resultCodeObj = queryResult.get("ResultCode");
                if (resultCodeObj == null) {
                    continue;
                }
                int resultCode = Integer.parseInt(String.valueOf(resultCodeObj));
                if (resultCode != 0) {
                    continue;
                }

                LOGGER.info("STK query confirmed success for job {} checkoutRequestId={}",
                        payment.getJobRequest().getId(), payment.getCheckoutRequestId());

                MpesaCallbackRequest callback = buildSyntheticSuccessCallback(payment.getCheckoutRequestId(), queryResult);
                handleMpesaCallback(callback, "STK_QUERY_RECONCILE");
            } catch (Exception e) {
                LOGGER.warn("STK query reconciliation failed for payment {}: {}",
                        payment.getId(), e.getMessage());
            }
        }
    }

    private MpesaCallbackRequest buildSyntheticSuccessCallback(String checkoutRequestId, Map<String, Object> queryResult) {
        String receipt = queryResult.get("MpesaReceiptNumber") != null
                ? String.valueOf(queryResult.get("MpesaReceiptNumber")) : null;
        Object amountObj = queryResult.get("Amount");
        Object phoneObj = queryResult.get("PhoneNumber");
        Object dateObj = queryResult.get("TransactionDate");

        java.util.ArrayList<MpesaCallbackRequest.Item> items = new java.util.ArrayList<>();
        if (amountObj != null) {
            items.add(new MpesaCallbackRequest.Item("Amount", amountObj));
        }
        if (receipt != null) {
            items.add(new MpesaCallbackRequest.Item("MpesaReceiptNumber", receipt));
        }
        if (phoneObj != null) {
            items.add(new MpesaCallbackRequest.Item("PhoneNumber", phoneObj));
        }
        if (dateObj != null) {
            items.add(new MpesaCallbackRequest.Item("TransactionDate", dateObj));
        }

        MpesaCallbackRequest.CallbackMetadata metadata = items.isEmpty()
                ? null
                : new MpesaCallbackRequest.CallbackMetadata(items);

        MpesaCallbackRequest.StkCallback stkCallback = new MpesaCallbackRequest.StkCallback(
                null,
                checkoutRequestId,
                0,
                "Reconciled via STK query",
                metadata
        );
        return new MpesaCallbackRequest(new MpesaCallbackRequest.Body(stkCallback));
    }

    /**
     * Auto-refund PENDING payments that timed out with no M-Pesa transaction evidence.
     */
    @Transactional
    public void refundExpiredPendingPayments() {
        try {
            LocalDateTime now = LocalDateTime.now();
            List<EscrowPayment> expiredPayments = escrowPaymentRepository.findAllByStatusAndTimeoutAtBefore(
                    EscrowPaymentStatus.PENDING, now);
            LOGGER.info("Checking {} expired PENDING payments for auto-refund", expiredPayments.size());
            
            for (EscrowPayment payment : expiredPayments) {
                try {
                    UUID jobId = payment.getJobRequest().getId();
                    
                    // ⚠️ CRITICAL SAFETY CHECK: If payment has transaction evidence, DO NOT auto-refund
                    // This prevents false refunds when MPESA callback is delayed
                    if (payment.getMpesaReceiptNumber() != null || payment.getTransactionDate() != null) {
                        LOGGER.warn("SAFETY: Skipping auto-refund for job {} - transaction evidence present (receipt={}, date={})",
                                jobId, payment.getMpesaReceiptNumber(), payment.getTransactionDate());
                        continue;
                    }

                    LOGGER.info("Auto-refunding PENDING payment for job {} (timeout, no callback received)", jobId);
                    refundEscrowBySystem(jobId, "Payment auto-refunded after timeout (no callback received).");
                    LOGGER.info("✓ Auto-refunded expired payment record {}", payment.getId());
                    
                } catch (Exception e) {
                    LOGGER.error("Failed to auto-refund expired payment record {}", payment.getId(), e);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error in expired payment refund scheduler: {}", e.getMessage(), e);
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
        return "MPESA_" + checkoutRequestId;
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
            // Handle both formats: yyyyMMddHHmmss and yyyyMMddHHmmssSSS
            if (raw.length() >= 14) {
                String formatted = raw.substring(0, 14);
                return LocalDateTime.parse(formatted, DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            }
            return LocalDateTime.parse(raw, DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        } catch (Exception e) {
            LOGGER.warn("Unable to parse transaction date from MPESA callback value: {}", raw, e);
            return null;
        }
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? null : value.toString();
    }

    /**
     * Process M-Pesa B2C transaction result (worker payout confirmation)
     * Delegates to B2cPayoutService for proper handling
     */
    @Transactional
    public void processMpesaB2cResult(String transactionId, String conversationId, String resultCode, String sourceIp) {
        try {
            if (!"0".equals(resultCode)) {
                LOGGER.warn("B2C result processing: failed with resultCode={} for conversationId={}", resultCode, conversationId);
                return;
            }

            LOGGER.info("Processing B2C result: txId={}, conversationId={}, resultCode={}, sourceIp={}", 
                transactionId, conversationId, resultCode, sourceIp);

            // B2cPayoutService handles the actual processing
            // This is called from webhook endpoint which doesn't have B2cPayoutService injected directly
            // If needed, inject B2cPayoutService and call: b2cPayoutService.processB2cSuccess(...)

        } catch (Exception e) {
            LOGGER.error("Error in processMpesaB2cResult: {}", e.getMessage(), e);
        }
    }

    /**
     * Process M-Pesa B2C transaction timeout
     * Delegates to B2cPayoutService for retry scheduling
     */
    @Transactional    public void processMpesaB2cTimeout(String conversationId, String responseCode, String responseDescription, String sourceIp) {
        try {
            LOGGER.error("B2C timeout processing: conversationId={}, code={}, desc={}, sourceIp={}", 
                conversationId, responseCode, responseDescription, sourceIp);
        } catch (Exception e) {
            LOGGER.error("Error in processMpesaB2cTimeout: {}", e.getMessage(), e);
        }
    }
}
