package com.kazikonnect.backend.features.platformwallet;

public enum PlatformTransactionType {
    ESCROW_RELEASE,
    WITHDRAWAL,
    WITHDRAWAL_CANCELLED,
    WITHDRAWAL_FAILED,
    ADMIN_ADJUSTMENT_CREDIT,
    ADMIN_ADJUSTMENT_DEBIT
}
