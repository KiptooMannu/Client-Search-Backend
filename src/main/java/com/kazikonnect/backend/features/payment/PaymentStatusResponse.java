package com.kazikonnect.backend.features.payment;

public record PaymentStatusResponse(
    String status,
    String id,
    String jobId,
    Double amount,
    String phoneNumber,
    String checkoutRequestId,
    String mpesaReceiptNumber,
    Double platformFee,
    Double workerAmount,
    String message,
    String createdAt,
    String timeoutAt
) {}
