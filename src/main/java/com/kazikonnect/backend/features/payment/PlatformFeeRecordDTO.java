package com.kazikonnect.backend.features.payment;

public record PlatformFeeRecordDTO(
        String jobId,
        String clientName,
        String workerName,
        String service,
        Double totalAmount,
        Double platformFee,
        Double workerNetAmount,
        String paymentStatus,
        String jobStatus,
        String transactionDate,
        String createdAt,
        String mpesaReceiptNumber
) {}
