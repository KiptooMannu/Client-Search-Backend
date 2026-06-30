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
import java.time.LocalDate;
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

    private final java.util.Map<String, LocalDateTime> lastQueryTimeMap = new java.util.concurrent.ConcurrentHashMap<>();

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

        // Use negotiated price if set, otherwise use total cost
        Double amount = Optional.ofNullable(job.getNegotiatedPrice()).orElse(job.getTotalCost());
        if (amount == null || amount <= 0) {
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

    @Transactional(readOnly = true)
    public List<PlatformFeeRecordDTO> getPlatformFeeRecords(String date, String status, String search) {
        String normalizedSearch = search != null ? search.trim().toLowerCase() : "";
        final LocalDate filterDate = (date != null && !date.isBlank()) ? LocalDate.parse(date) : null;
        String normalizedStatus = status != null ? status.trim() : "";

        return escrowPaymentRepository.findAll().stream()
                .filter(payment -> payment.getJobRequest() != null)
                .filter(payment -> {
                    if (filterDate == null) {
                        return true;
                    }
                    LocalDateTime tx = payment.getTransactionDate() != null
                            ? payment.getTransactionDate()
                            : payment.getCreatedAt();
                    return tx != null && tx.toLocalDate().equals(filterDate);
                })
                .filter(payment -> normalizedStatus.isBlank()
                        || "All".equalsIgnoreCase(normalizedStatus)
                        || payment.getStatus().name().equalsIgnoreCase(normalizedStatus))
                .filter(payment -> {
                    if (normalizedSearch.isBlank()) {
                        return true;
                    }
                    JobRequest job = payment.getJobRequest();
                    String clientName = job.getClient() != null ? job.getClient().getFullName() : "";
                    String workerName = job.getWorker() != null && job.getWorker().getFullName() != null
                            ? job.getWorker().getFullName() : "";
                    String haystack = String.join(" ",
                            job.getId().toString(),
                            clientName,
                            workerName,
                            job.getDescription() != null ? job.getDescription() : "",
                            payment.getStatus().name(),
                            job.getStatus().name()
                    ).toLowerCase();
                    return haystack.contains(normalizedSearch);
                })
                .sorted((a, b) -> {
                    LocalDateTime bTime = b.getTransactionDate() != null ? b.getTransactionDate() : b.getCreatedAt();
                    LocalDateTime aTime = a.getTransactionDate() != null ? a.getTransactionDate() : a.getCreatedAt();
                    if (bTime == null && aTime == null) return 0;
                    if (bTime == null) return 1;
                    if (aTime == null) return -1;
                    return bTime.compareTo(aTime);
                })
                .map(payment -> {
                    JobRequest job = payment.getJobRequest();
                    double total = payment.getAmount() != null ? payment.getAmount() : 0.0;
                    double fee = payment.getPlatformFee() != null ? payment.getPlatformFee() : calculatePlatformFee(total);
                    double net = payment.getWorkerAmount() != null ? payment.getWorkerAmount() : (total - fee);
                    return new PlatformFeeRecordDTO(
                            job.getId().toString(),
                            job.getClient() != null ? job.getClient().getFullName() : "Client",
                            job.getWorker() != null && job.getWorker().getFullName() != null
                                    ? job.getWorker().getFullName() : "Worker",
                            job.getDescription(),
                            total,
                            fee,
                            net,
                            payment.getStatus().name(),
                            job.getStatus().name(),
                            formatDateTime(payment.getTransactionDate()),
                            formatDateTime(payment.getCreatedAt()),
                            payment.getMpesaReceiptNumber()
                    );
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public void releaseEscrow(UUID jobId, Principal principal) {
        User actor = getActor(principal);
        JobRequest job = jobRequestRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found."));

        if (actor.getRole() == com.kazikonnect.backend.features.auth.UserRole.CLIENT) {
            if (job.getClient() == null || !job.getClient().getId().equals(actor.getId())) {
                throw new RuntimeException("Forbidden: you are not permitted to approve this payment.");
            }
        }

        EscrowPayment payment = escrowPaymentRepository.findTopByJobRequestIdAndStatusInOrderByCreatedAtDesc(jobId,
                        List.of(EscrowPaymentStatus.SUCCESS, EscrowPaymentStatus.ESCROWED, EscrowPaymentStatus.RELEASED))
                .orElseThrow(() -> new RuntimeException("Payment record not found or not ready to release. Payment must be confirmed first."));

        if (payment.getStatus() == EscrowPaymentStatus.RELEASED) {
            job.setStatus(JobStatus.APPROVED);
            if (job.getApprovedAt() == null) {
                job.setApprovedAt(LocalDateTime.now());
            }
            jobRequestRepository.save(job);
            return;
        }

        ensureStatusTransition(payment, EscrowPaymentStatus.RELEASED);

        double paymentAmount = payment.getAmount() != null ? payment.getAmount() : 0.0;
        double fee = payment.getPlatformFee() != null ? payment.getPlatformFee() : calculatePlatformFee(paymentAmount);
        double workerNet = payment.getWorkerAmount() != null ? payment.getWorkerAmount() : (paymentAmount - fee);

        payment.setPlatformFee(fee);
        payment.setWorkerAmount(workerNet);
        payment.setStatus(EscrowPaymentStatus.RELEASED);
        payment.setMessage("Payment released to worker. Platform fee: KES " + fee);
        payment.setUpdatedAt(LocalDateTime.now());

        // ATOMIC: Update payment and credit wallet in same transaction
        escrowPaymentRepository.save(payment);
        
        if (job.getWorker() == null || job.getWorker().getUser() == null) {
            throw new RuntimeException("Worker account not found for this job.");
        }

        job.setStatus(JobStatus.APPROVED);
        job.setApprovedAt(LocalDateTime.now());
        
        // Set review required flag if no review exists yet
        if (job.getReview() == null) {
            job.setReviewRequired(true);
        }
        
        jobRequestRepository.save(job);

        walletService.creditWallet(job.getWorker().getUser(), workerNet,
                "Payment release for job " + jobId);

        try {
            saveAuditLog(payment, "PAYMENT_RELEASED", principal, "Payment released to worker", null);
        } catch (Exception auditEx) {
            LOGGER.warn("Payment released for job {} but audit log failed: {}", jobId, auditEx.getMessage());
        }
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

    /**
     * Client-submitted manual receipt verification.
     * Called when the Safaricom webhook was missed and the payment was captured
     * by the STK-Query fallback — leaving mpesaReceiptNumber=null.
     *
     * Rules:
     *  - Receipt must match the Safaricom format (capital letters + digits, 10 chars).
     *  - We refuse to overwrite a real receipt that already exists.
     *  - Payment must be ESCROWED or PENDING (webhook may still be in-flight).
     *  - Sets transactionDate to now() if it was null (approximate is better than nothing).
     */
    @Transactional
    public void verifyReceiptManual(UUID jobId, String receiptNumber, Principal principal) {
        if (receiptNumber == null || !receiptNumber.matches("[A-Z0-9]{10,12}")) {
            throw new RuntimeException(
                "Invalid M-Pesa receipt number. It must be 10-12 uppercase letters/digits (e.g. QHY1ABCDEF).");
        }

        EscrowPayment payment = escrowPaymentRepository
                .findTopByJobRequestIdOrderByCreatedAtDesc(jobId)
                .orElseThrow(() -> new RuntimeException("No payment record found for this job."));

        // Guard: don't overwrite a real receipt that was already captured
        if (payment.getMpesaReceiptNumber() != null
                && !payment.getMpesaReceiptNumber().isBlank()
                && !payment.getMpesaReceiptNumber().startsWith("MANUAL_")) {
            throw new RuntimeException(
                "A verified receipt (" + payment.getMpesaReceiptNumber() + ") is already recorded for this payment.");
        }

        // Only accept for payments that are in a state where receipt is meaningful
        EscrowPaymentStatus status = payment.getStatus();
        if (status == EscrowPaymentStatus.RELEASED
                || status == EscrowPaymentStatus.REFUNDED
                || status == EscrowPaymentStatus.FAILED) {
            throw new RuntimeException(
                "Cannot update receipt for a payment in status: " + status +
                ". Contact support if you believe this is an error.");
        }

        LOGGER.info("Manual receipt submission for job {} by {}: receipt={}, previous={}",
                jobId, principal != null ? principal.getName() : "system",
                receiptNumber, payment.getMpesaReceiptNumber());

        payment.setMpesaReceiptNumber(receiptNumber);
        if (payment.getTransactionDate() == null) {
            payment.setTransactionDate(LocalDateTime.now());
        }

        // If payment was ESCROWED but had no receipt, this receipt fills the gap — keep ESCROWED
        // If payment was still PENDING, the receipt proves the money was collected — mark ESCROWED
        if (status == EscrowPaymentStatus.PENDING) {
            ensureStatusTransition(payment, EscrowPaymentStatus.ESCROWED);
            payment.setStatus(EscrowPaymentStatus.ESCROWED);
        }

        payment.setMessage("Receipt manually verified by client: " + receiptNumber);
        payment.setUpdatedAt(LocalDateTime.now());
        escrowPaymentRepository.save(payment);

        saveAuditLog(payment, "RECEIPT_MANUAL_VERIFIED", principal,
                "Client submitted real M-Pesa receipt: " + receiptNumber, null);
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
            case 2001 -> {
                if (resultDesc != null && resultDesc.toLowerCase().contains("initiator")) {
                    yield "Incorrect M-Pesa PIN or security credential. Please verify your payment configuration.";
                }
                yield "Unable to communicate with M-Pesa. Please try again.";
            }
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

        String callbackKey = "STK_QUERY_RECONCILE".equals(remoteIp)
                ? "STK_QUERY_" + checkoutRequestId
                : buildCallbackIdempotencyKey(checkoutRequestId);

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
        if (receiptNumber != null && !receiptNumber.isBlank()) {
            payment.setMpesaReceiptNumber(receiptNumber);
        }
        if (amount != null && amount > 0) payment.setAmount(amount);
        if (transactionDate != null) {
            payment.setTransactionDate(transactionDate);
        }

        // Handle different result codes appropriately
        if (resultCode == null) {
            // No response from M-Pesa
            payment.setStatus(EscrowPaymentStatus.FAILED);
            payment.setFailureReason("NO_RESPONSE_FROM_MPESA");
            payment.setMessage("No response received from M-Pesa. Please try again.");
            LOGGER.error("MPESA callback with null resultCode for CheckoutRequestID={}", checkoutRequestId);
            
        } else if (resultCode == 0) {
            // ✓ SUCCESSFUL PAYMENT - Payment received and validated by M-Pesa
            double capturedAmount = amount != null && amount > 0 ? amount : (payment.getAmount() != null ? payment.getAmount() : 0.0);
            double fee = calculatePlatformFee(capturedAmount);
            payment.setStatus(EscrowPaymentStatus.ESCROWED);
            payment.setPlatformFee(fee);
            payment.setWorkerAmount(capturedAmount - fee);
            payment.setMessage("Payment received and held in escrow (KES " + String.format("%.2f", capturedAmount)
                    + "). Platform fee recorded: KES " + String.format("%.2f", fee)
                    + ". Funds release after client approves completed work.");
            payment.setFailureReason(null);
            LOGGER.info("✓✓✓ PAYMENT ESCROWED for CheckoutRequestID={}, Receipt={}, Amount={}, Fee={}",
                checkoutRequestId, receiptNumber, capturedAmount, fee);

            // Update job status when payment is captured in escrow
            JobRequest job = payment.getJobRequest();
            if (job != null) {
                if (job.getStatus() == JobStatus.PENDING
                        || job.getStatus() == JobStatus.ACCEPTED
                        || job.getStatus() == JobStatus.AWAITING_FUNDING
                        || job.getStatus() == JobStatus.ASSIGNED) {
                    job.setStatus(JobStatus.IN_PROGRESS);
                    job.setEscrowFunded(true);
                    jobRequestRepository.save(job);
                    LOGGER.info("Job {} status -> IN_PROGRESS, escrowFunded -> true (payment captured in escrow)", job.getId());
                } else {
                    LOGGER.info("Job {} already has status {}, not transitioning", job.getId(), job.getStatus());
                }
            } else {
                LOGGER.warn("No job associated with payment for CheckoutRequestID={}", checkoutRequestId);
            }
            
        } else if (resultCode == 1032) {
            payment.setStatus(EscrowPaymentStatus.FAILED);
            payment.setFailureReason("USER_CANCELLED");
            payment.setMessage("Payment cancelled by you.");
            LOGGER.warn("Payment cancelled by user for CheckoutRequestID={}", checkoutRequestId);

        } else if (resultCode == 1031 || resultCode == 1001) {
            payment.setStatus(EscrowPaymentStatus.FAILED);
            payment.setFailureReason("TIMEOUT");
            payment.setMessage("Payment request timed out before PIN was entered.");
            LOGGER.warn("Payment timed out (user did not enter PIN in time) for CheckoutRequestID={}", checkoutRequestId);

        } else if (resultCode == 1 || resultCode == 1037) {
            payment.setStatus(EscrowPaymentStatus.FAILED);
            payment.setFailureReason("INSUFFICIENT_FUNDS");
            payment.setMessage("Insufficient M-Pesa balance. Please top up your account.");
            LOGGER.warn("Payment failed - insufficient funds for CheckoutRequestID={}", checkoutRequestId);

        } else if (resultCode == 1034) {
            payment.setStatus(EscrowPaymentStatus.FAILED);
            payment.setFailureReason("WRONG_PIN");
            payment.setMessage("Incorrect M-Pesa PIN entered. Please try again.");
            LOGGER.warn("Payment failed - wrong PIN for CheckoutRequestID={}", checkoutRequestId);

        } else if (resultCode == 1035) {
            payment.setStatus(EscrowPaymentStatus.FAILED);
            payment.setFailureReason("PIN_BLOCKED");
            payment.setMessage("Your M-Pesa PIN has been blocked due to too many wrong attempts. Please reset it or contact Safaricom.");
            LOGGER.warn("Payment failed - PIN blocked for CheckoutRequestID={}", checkoutRequestId);

        } else if (resultCode == 1033) {
            payment.setStatus(EscrowPaymentStatus.FAILED);
            payment.setFailureReason("ACCOUNT_INACTIVE");
            payment.setMessage("Your M-Pesa account is inactive. Please contact Safaricom support.");
            LOGGER.warn("Payment failed - account inactive for CheckoutRequestID={}", checkoutRequestId);

        } else if (resultCode == 1036) {
            payment.setStatus(EscrowPaymentStatus.FAILED);
            payment.setFailureReason("NOT_STK_CAPABLE");
            payment.setMessage("Your phone number does not support M-Pesa STK. Please try with a different number (254...).");
            LOGGER.warn("Payment failed - phone not STK capable for CheckoutRequestID={}", checkoutRequestId);

        } else if (resultCode == 1030) {
            payment.setStatus(EscrowPaymentStatus.FAILED);
            payment.setFailureReason("REQUEST_CANCELLED");
            payment.setMessage("Payment request was cancelled. Please retry.");
            LOGGER.warn("Payment request cancelled for CheckoutRequestID={}", checkoutRequestId);

        } else if (resultCode == 2001) {
            // ResultCode 2001: "The initiator information is invalid" - typically wrong PIN or B2C security credential
            String errorMsg = resultDesc != null && resultDesc.toLowerCase().contains("initiator") 
                ? "Incorrect M-Pesa PIN or security credential. Please verify your payment configuration."
                : "Unable to communicate with M-Pesa. Please try again.";
            payment.setStatus(EscrowPaymentStatus.FAILED);
            payment.setFailureReason("INITIATOR_INVALID");
            payment.setMessage(errorMsg);
            LOGGER.error("Payment failed - initiator/security issue for CheckoutRequestID={}: {}", checkoutRequestId, resultDesc);

        } else if (resultCode == 2002) {
            payment.setStatus(EscrowPaymentStatus.FAILED);
            payment.setFailureReason("SYSTEM_ERROR");
            payment.setMessage("M-Pesa system error. Please try again in a few minutes.");
            LOGGER.error("Payment failed - M-Pesa system error for CheckoutRequestID={}", checkoutRequestId);

        } else {
            payment.setStatus(EscrowPaymentStatus.FAILED);
            payment.setFailureReason(mapFailureReason(resultCode, resultDesc));
            payment.setMessage(mapFailureMessage(resultCode, resultDesc));
            LOGGER.error("✗ PAYMENT FAILED - Unknown error code {} ({}) for CheckoutRequestID={}", 
                resultCode, resultDesc, checkoutRequestId);
        }
        
        payment.setUpdatedAt(LocalDateTime.now());

        // ATOMIC: Update payment and log in same transaction
        EscrowPayment savedPayment = escrowPaymentRepository.save(payment);

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

        String humanReason = switch (resultCode) {
            case 1032 -> "Payment cancelled by user";
            case 1031, 1001 -> "Payment timed out";
            case 1, 1037 -> "Insufficient funds";
            case 1034 -> "Wrong PIN";
            case 1035 -> "PIN blocked";
            case 1033 -> "Account inactive";
            case 1036 -> "Phone not STK capable";
            case 1030 -> "Request cancelled";
            case 2001 -> "Network failure";
            case 2002 -> "M-Pesa system error";
            case null -> "No response from M-Pesa";
            default -> payment.getFailureReason();
        };

        LOGGER.info("MPESA callback processed for CheckoutRequestID={} with ResultCode={} and final status={}, reason={}",
            checkoutRequestId, resultCode, savedPayment.getStatus(), humanReason);
    }

    /**
     * Reconcile PENDING payments by querying Safaricom STK status API (fallback when callback is missed).
     */
    @Transactional
    public void reconcilePendingStkPayments() {
        LocalDateTime now = LocalDateTime.now();
        List<EscrowPayment> pendingPayments = escrowPaymentRepository.findByStatus(EscrowPaymentStatus.PENDING);

        for (EscrowPayment payment : pendingPayments) {
            String checkoutId = payment.getCheckoutRequestId();
            if (checkoutId == null || checkoutId.isBlank()) {
                continue;
            }
            // Skip newly created payments (less than 20 seconds ago) to let user type PIN
            if (payment.getCreatedAt() != null && payment.getCreatedAt().isAfter(now.minusSeconds(20))) {
                continue;
            }
            // Skip if queried in the last 30 seconds to prevent Spike Arrest rate limiting
            LocalDateTime lastQuery = lastQueryTimeMap.get(checkoutId);
            if (lastQuery != null && lastQuery.isAfter(now.minusSeconds(30))) {
                continue;
            }
            try {
                lastQueryTimeMap.put(checkoutId, LocalDateTime.now());
                Map<String, Object> queryResult = mpesaService.queryStkPushStatus(checkoutId);
                Object resultCodeObj = queryResult.get("ResultCode");
                if (resultCodeObj == null) {
                    continue;
                }
                int resultCode = Integer.parseInt(String.valueOf(resultCodeObj));
                if (resultCode != 0) {
                    continue;
                }
                lastQueryTimeMap.remove(checkoutId);

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
        String receipt = safeString(queryResult.get("MpesaReceiptNumber"));
        Object amountObj = queryResult.get("Amount");
        Object phoneObj = queryResult.get("PhoneNumber");
        Object dateObj = queryResult.get("TransactionDate");

        Object resultParamsObj = queryResult.get("ResultParameters");
        if (resultParamsObj instanceof Map<?, ?> resultParamsMap) {
            Object resultParamArray = resultParamsMap.get("ResultParameter");
            if (resultParamArray instanceof List<?> paramList) {
                for (Object param : paramList) {
                    if (param instanceof Map<?, ?> paramMap) {
                        String key = safeString(paramMap.get("Key"));
                        Object value = paramMap.get("Value");
                        if (key == null || value == null) continue;
                        switch (key) {
                            case "TransactionAmount" -> {
                                if (amountObj == null) amountObj = value;
                            }
                            case "TransactionReceipt" -> {
                                if (receipt == null) receipt = safeString(value);
                            }
                            case "PhoneNumber" -> {
                                if (phoneObj == null) phoneObj = value;
                            }
                            case "TransactionDateTime" -> {
                                if (dateObj == null) dateObj = value;
                            }
                            default -> {}
                        }
                    }
                }
            }
        }

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
            if (raw.length() >= 14) {
                String formatted = raw.substring(0, 14);
                return LocalDateTime.parse(formatted, DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            }
            return LocalDateTime.parse(raw, DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        } catch (Exception e) {
            try {
                return LocalDateTime.parse(raw, DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"));
            } catch (Exception ex) {
                LOGGER.warn("Unable to parse transaction date from MPESA callback value: {}", raw, ex);
                return null;
            }
        }
    }

    @Transactional
    public void autoReleaseEscrow(UUID jobId) {
        EscrowPayment payment = escrowPaymentRepository.findTopByJobRequestIdAndStatusInOrderByCreatedAtDesc(jobId,
                        List.of(EscrowPaymentStatus.ESCROWED))
                .orElseThrow(() -> new RuntimeException("Escrow payment not found for job: " + jobId));

        ensureStatusTransition(payment, EscrowPaymentStatus.RELEASED);

        double paymentAmount = payment.getAmount() != null ? payment.getAmount() : 0.0;
        double fee = payment.getPlatformFee() != null ? payment.getPlatformFee() : calculatePlatformFee(paymentAmount);
        double workerNet = payment.getWorkerAmount() != null ? payment.getWorkerAmount() : (paymentAmount - fee);

        payment.setPlatformFee(fee);
        payment.setWorkerAmount(workerNet);
        payment.setStatus(EscrowPaymentStatus.RELEASED);
        payment.setMessage("System auto-released escrow to worker wallet. Platform fee: KES " + fee);
        payment.setUpdatedAt(LocalDateTime.now());

        escrowPaymentRepository.save(payment);

        JobRequest job = payment.getJobRequest();
        if (job.getWorker() == null || job.getWorker().getUser() == null) {
            throw new RuntimeException("Worker account not found for this job.");
        }

        job.setStatus(JobStatus.APPROVED);
        job.setApprovedAt(LocalDateTime.now());
        job.setReviewRequired(job.getReview() == null);
        jobRequestRepository.save(job);

        walletService.creditWallet(job.getWorker().getUser(), workerNet,
                "Auto-release payment for job " + jobId);

        saveAuditLog(payment, "SYSTEM_AUTO_RELEASED", null, "System auto-released escrow", null);
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
    @Transactional
    public void processMpesaB2cTimeout(String conversationId, String responseCode, String responseDescription, String sourceIp) {
        try {
            LOGGER.error("B2C timeout processing: conversationId={}, code={}, desc={}, sourceIp={}", 
                conversationId, responseCode, responseDescription, sourceIp);
        } catch (Exception e) {
            LOGGER.error("Error in processMpesaB2cTimeout: {}", e.getMessage(), e);
        }
    }
}
