package com.kazikonnect.backend.features.payment;

import com.kazikonnect.backend.features.auth.User;
import com.kazikonnect.backend.features.auth.UserRepository;
import com.kazikonnect.backend.features.wallet.WalletService;
import com.kazikonnect.backend.features.worker.JobRequest;
import com.kazikonnect.backend.features.worker.JobRequestRepository;
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

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class PaymentService {

    private final JobRequestRepository jobRequestRepository;
    private final UserRepository userRepository;
    private final EscrowPaymentRepository escrowPaymentRepository;
    private final WebhookProcessedLogRepository webhookProcessedLogRepository;
    private final PaymentAuditLogRepository paymentAuditLogRepository;
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
        StkPushResponse pushResponse = mpesaService.initiateStkPush(normalizedPhone, amount, jobId.toString(),
                "Escrow payment for job " + jobId);

        Optional<EscrowPayment> latestPayment = escrowPaymentRepository.findTopByJobRequestIdOrderByCreatedAtDesc(jobId);
        if (latestPayment.isPresent() && (latestPayment.get().getStatus() == EscrowPaymentStatus.PENDING
                || latestPayment.get().getStatus() == EscrowPaymentStatus.ESCROWED
                || latestPayment.get().getStatus() == EscrowPaymentStatus.SUCCESS)) {
            throw new RuntimeException("A payment is already pending or held for this job. Please resolve the existing escrow before retrying.");
        }

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
                    "No escrow payment found for this job.",
                    null,
                    null,
                    null,
                    null
            );
        }

        EscrowPayment payment = paymentOpt.get();
        return new PaymentStatusResponse(
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
            throw new RuntimeException("Forbidden: you are not permitted to release this escrow.");
        }

        EscrowPayment payment = escrowPaymentRepository.findTopByJobRequestIdAndStatusInOrderByCreatedAtDesc(jobId,
                        List.of(EscrowPaymentStatus.SUCCESS, EscrowPaymentStatus.ESCROWED))
                .orElseThrow(() -> new RuntimeException("Escrow payment not found or not ready to release."));

        ensureStatusTransition(payment, EscrowPaymentStatus.RELEASED);

        double fee = calculatePlatformFee(payment.getAmount());
        payment.setPlatformFee(fee);
        payment.setWorkerAmount(payment.getAmount() - fee);
        payment.setStatus(EscrowPaymentStatus.RELEASED);
        payment.setMessage("Escrow released to worker. Platform fee: KES " + fee);
        payment.setUpdatedAt(LocalDateTime.now());
        escrowPaymentRepository.save(payment);
        saveAuditLog(payment, "ESCROW_RELEASED", principal, "Escrow released to worker", null);

        if (job.getWorker() == null || job.getWorker().getUser() == null) {
            throw new RuntimeException("Worker account not found for this job.");
        }

        walletService.creditWallet(job.getWorker().getUser(), payment.getWorkerAmount(),
                "Escrow release for job " + jobId);
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
                .orElseThrow(() -> new RuntimeException("Escrow payment not found or not eligible for refund."));

        if (payment.getStatus() != EscrowPaymentStatus.SUCCESS
                && payment.getStatus() != EscrowPaymentStatus.PENDING
                && payment.getStatus() != EscrowPaymentStatus.ESCROWED) {
            throw new RuntimeException("Only pending, captured, or escrowed payments can be refunded.");
        }

        payment.setStatus(EscrowPaymentStatus.REFUNDED);
        payment.setMessage("Escrow refunded to payer.");
        payment.setUpdatedAt(LocalDateTime.now());
        escrowPaymentRepository.save(payment);
        saveAuditLog(payment, "ESCROW_REFUNDED", principal, "Refund requested by actor", null);
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
        payment.setMessage(reason != null ? reason : "Escrow refunded by system.");
        payment.setUpdatedAt(LocalDateTime.now());
        escrowPaymentRepository.save(payment);
        saveAuditLog(payment, "ESCROW_REFUNDED", null, reason, null);
    }

    private String mapFailureReason(Integer resultCode, String resultDesc) {
        if (resultCode == null) {
            return resultDesc != null && !resultDesc.isBlank() ? resultDesc.toUpperCase() : "UNKNOWN_ERROR";
        }
        return switch (resultCode) {
            case 1032 -> "USER_CANCELLED";
            case 1031 -> "TIMEOUT";
            case 1 -> "INSUFFICIENT_FUNDS";
            case 1030 -> "REQUEST_CANCELLED";
            default -> "MPESA_ERROR_" + resultCode;
        };
    }

    private String mapFailureMessage(Integer resultCode, String resultDesc) {
        if (resultCode == null) {
            return resultDesc != null && !resultDesc.isBlank() ? resultDesc : "Payment failed.";
        }
        return switch (resultCode) {
            case 1032 -> "Payment cancelled by user.";
            case 1031 -> "No response from user.";
            case 1 -> "Insufficient funds.";
            case 1030 -> "STK request cancelled.";
            default -> resultDesc != null && !resultDesc.isBlank() ? resultDesc : "Payment failed.";
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
        if (webhookProcessedLogRepository.existsById(callbackKey)) {
            LOGGER.info("Duplicate MPESA callback ignored for CheckoutRequestID={}", checkoutRequestId);
            return;
        }

        if (!mpesaService.isAcceptedCallbackSource(remoteIp)) {
            LOGGER.warn("Rejected MPESA callback from unauthorized source {} for CheckoutRequestID={}", remoteIp, checkoutRequestId);
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
            payment.setStatus(EscrowPaymentStatus.ESCROWED);
            payment.setMessage("Payment captured and held in escrow.");
            payment.setFailureReason(null);
        } else {
            payment.setStatus(EscrowPaymentStatus.FAILED);
            payment.setFailureReason(mapFailureReason(resultCode, resultDesc));
            payment.setMessage(mapFailureMessage(resultCode, resultDesc));
        }
        payment.setUpdatedAt(LocalDateTime.now());
        escrowPaymentRepository.save(payment);
        saveAuditLog(payment, "MPESA_CALLBACK", null,
                success ? "Payment held in escrow" : payment.getFailureReason(),
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
                refundEscrowBySystem(payment.getJobRequest().getId(), "Escrow auto-refunded after timeout.");
            } catch (Exception e) {
                LOGGER.error("Failed to auto-refund expired escrow payment {}", payment.getId(), e);
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
                    String.format("Escrow payment may not transition from %s to %s", payment.getStatus(), targetStatus));
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
}
