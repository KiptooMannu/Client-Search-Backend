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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class PaymentService {

    private final JobRequestRepository jobRequestRepository;
    private final UserRepository userRepository;
    private final EscrowPaymentRepository escrowPaymentRepository;
    private final WebhookProcessedLogRepository webhookProcessedLogRepository;
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

        EscrowPayment payment = escrowPaymentRepository.findByJobRequestId(jobId)
                .orElse(EscrowPayment.builder()
                        .jobRequest(job)
                        .status(EscrowPaymentStatus.PENDING)
                        .amount(amount)
                        .build());

        payment.setPhoneNumber(normalizedPhone);
        payment.setCheckoutRequestId(pushResponse.checkoutRequestId());
        payment.setMessage(pushResponse.message());
        payment.setStatus(EscrowPaymentStatus.PENDING);
        payment.setTimeoutAt(LocalDateTime.now().plusMinutes(15));
        payment.setUpdatedAt(LocalDateTime.now());

        escrowPaymentRepository.save(payment);
        return pushResponse;
    }

    @Transactional(readOnly = true)
    public PaymentStatusResponse getPaymentStatus(UUID jobId) {
        Optional<EscrowPayment> paymentOpt = escrowPaymentRepository.findByJobRequestId(jobId);
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
                formatDateTime(payment.getTimeoutAt())
        );
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

        EscrowPayment payment = escrowPaymentRepository.findByJobRequestId(jobId)
                .orElseThrow(() -> new RuntimeException("Escrow payment not found."));

        ensureStatusTransition(payment, EscrowPaymentStatus.RELEASED);

        double fee = calculatePlatformFee(payment.getAmount());
        payment.setPlatformFee(fee);
        payment.setWorkerAmount(payment.getAmount() - fee);
        payment.setStatus(EscrowPaymentStatus.RELEASED);
        payment.setMessage("Escrow released to worker. Platform fee: KES " + fee);
        payment.setUpdatedAt(LocalDateTime.now());
        escrowPaymentRepository.save(payment);

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

        EscrowPayment payment = escrowPaymentRepository.findByJobRequestId(jobId)
                .orElseThrow(() -> new RuntimeException("Escrow payment not found."));

        if (payment.getStatus() != EscrowPaymentStatus.SUCCESS
                && payment.getStatus() != EscrowPaymentStatus.PENDING) {
            throw new RuntimeException("Only pending or successful payments can be refunded.");
        }

        payment.setStatus(EscrowPaymentStatus.REFUNDED);
        payment.setMessage("Escrow refunded to payer.");
        payment.setUpdatedAt(LocalDateTime.now());
        escrowPaymentRepository.save(payment);
    }

    @Transactional
    public void refundEscrowBySystem(UUID jobId, String reason) {
        EscrowPayment payment = escrowPaymentRepository.findByJobRequestId(jobId)
                .orElse(null);
        if (payment == null) {
            return;
        }
        ensureStatusTransition(payment, EscrowPaymentStatus.REFUNDED);
        payment.setStatus(EscrowPaymentStatus.REFUNDED);
        payment.setMessage(reason != null ? reason : "Escrow refunded by system.");
        payment.setUpdatedAt(LocalDateTime.now());
        escrowPaymentRepository.save(payment);
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
        payment.setMessage(resultDesc != null ? resultDesc : "MPESA callback received.");
        payment.setStatus(resultCode != null && resultCode == 0
                ? EscrowPaymentStatus.SUCCESS
                : EscrowPaymentStatus.FAILED);
        payment.setUpdatedAt(LocalDateTime.now());
        escrowPaymentRepository.save(payment);

        webhookProcessedLogRepository.save(WebhookProcessedLog.builder()
                .id(callbackKey)
                .checkoutRequestId(checkoutRequestId)
                .resultCode(resultCode)
                .resultDescription(resultDesc)
                .payload(String.valueOf(callbackRequest))
                .build());

        LOGGER.info("MPESA callback processed for CheckoutRequestID={} with ResultCode={} and status={}", checkoutRequestId, resultCode, payment.getStatus());
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
