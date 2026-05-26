package com.kazikonnect.backend.features.payment;

import com.kazikonnect.backend.features.auth.User;
import com.kazikonnect.backend.features.auth.UserRepository;
import com.kazikonnect.backend.features.wallet.WalletService;
import com.kazikonnect.backend.features.worker.JobRequest;
import com.kazikonnect.backend.features.worker.JobRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
                formatDateTime(payment.getCreatedAt()),
                formatDateTime(payment.getTimeoutAt())
        );
    }

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

        if (payment.getStatus() != EscrowPaymentStatus.ESCROWED
                && payment.getStatus() != EscrowPaymentStatus.PENDING) {
            throw new RuntimeException("Only pending or escrowed payments can be refunded.");
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
    public void handleMpesaCallback(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return;
        }

        Map<String, Object> body = cast(payload.get("Body"));
        if (body == null) {
            return;
        }

        Map<String, Object> stkCallback = cast(body.get("stkCallback"));
        if (stkCallback == null) {
            return;
        }

        Integer resultCode = toInteger(stkCallback.get("ResultCode"));
        String checkoutRequestId = safeString(stkCallback.get("CheckoutRequestID"));
        String resultDesc = safeString(stkCallback.get("ResultDesc"));

        if (checkoutRequestId == null || checkoutRequestId.isBlank()) {
            return;
        }

        String callbackKey = buildCallbackIdempotencyKey(checkoutRequestId, resultCode);
        if (webhookProcessedLogRepository.existsById(callbackKey)) {
            return;
        }

        EscrowPayment payment = escrowPaymentRepository.findByCheckoutRequestIdForUpdate(checkoutRequestId)
                .orElse(null);

        String phoneNumber = null;
        String receiptNumber = null;
        Double amount = null;

        Map<String, Object> callbackMetadata = cast(stkCallback.get("CallbackMetadata"));
        if (callbackMetadata != null) {
            List<Map<String, Object>> items = cast(callbackMetadata.get("Item"));
            if (items != null) {
                for (Map<String, Object> item : items) {
                    String name = safeString(item.get("Name"));
                    Object value = item.get("Value");
                    switch (name) {
                        case "MpesaReceiptNumber" -> receiptNumber = safeString(value);
                        case "Amount" -> amount = toDouble(value);
                        case "PhoneNumber" -> phoneNumber = safeString(value);
                        default -> {}
                    }
                }
            }
        }

        if (payment == null) {
            webhookProcessedLogRepository.save(WebhookProcessedLog.builder()
                    .id(callbackKey)
                    .checkoutRequestId(checkoutRequestId)
                    .resultCode(resultCode)
                    .resultDescription(resultDesc)
                    .payload(String.valueOf(payload))
                    .build());
            return;
        }

        if (payment.getStatus() == EscrowPaymentStatus.RELEASED
                || payment.getStatus() == EscrowPaymentStatus.REFUNDED) {
            webhookProcessedLogRepository.save(WebhookProcessedLog.builder()
                    .id(callbackKey)
                    .checkoutRequestId(checkoutRequestId)
                    .resultCode(resultCode)
                    .resultDescription(resultDesc)
                    .payload(String.valueOf(payload))
                    .build());
            return;
        }

        payment.setPhoneNumber(phoneNumber != null ? phoneNumber : payment.getPhoneNumber());
        payment.setMpesaReceiptNumber(receiptNumber);
        payment.setAmount(amount != null ? amount : payment.getAmount());
        payment.setMessage(resultDesc);
        payment.setStatus(resultCode != null && resultCode == 0
                ? EscrowPaymentStatus.ESCROWED
                : EscrowPaymentStatus.FAILED);
        payment.setUpdatedAt(LocalDateTime.now());
        escrowPaymentRepository.save(payment);
        webhookProcessedLogRepository.save(WebhookProcessedLog.builder()
                .id(callbackKey)
                .checkoutRequestId(checkoutRequestId)
                .resultCode(resultCode)
                .resultDescription(resultDesc)
                .payload(String.valueOf(payload))
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

    private String buildCallbackIdempotencyKey(String checkoutRequestId, Integer resultCode) {
        return checkoutRequestId + "_" + (resultCode == null ? "null" : resultCode);
    }

    @SuppressWarnings("unchecked")
    private <T> T cast(Object value) {
        return (T) value;
    }

    private String safeString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer toInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
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

    private String formatDateTime(LocalDateTime value) {
        return value == null ? null : value.toString();
    }
}
