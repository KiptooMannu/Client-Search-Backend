package com.kazikonnect.backend.features.dispute;

public enum ResolutionType {
    FULL_REFUND_TO_CLIENT,      // Full amount refunded to client
    FULL_PAYMENT_TO_WORKER,      // Full amount paid to worker
    SPLIT                        // Amount split between both parties
}
