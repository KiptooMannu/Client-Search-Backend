package com.kazikonnect.backend.features.payment;

public record PaymentReceiptDTO(
    String receiptNumber,
    String date,
    Double amount,
    Double platformFee,
    Double workerAmount,
    String status
) {}
