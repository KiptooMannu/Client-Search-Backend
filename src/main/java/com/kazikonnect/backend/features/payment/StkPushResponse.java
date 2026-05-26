package com.kazikonnect.backend.features.payment;

public record StkPushResponse(
    String status,
    String checkoutRequestId,
    String message
) {}
