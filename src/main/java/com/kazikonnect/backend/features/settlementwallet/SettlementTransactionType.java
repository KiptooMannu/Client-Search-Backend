package com.kazikonnect.backend.features.settlementwallet;

public enum SettlementTransactionType {
    REFUND,
    PARTIAL_REFUND,
    ESCROW_CANCELLATION,
    WORKER_REJECTION_REFUND,
    DISPUTE_AWARD,
    PARTIAL_ESCROW_RELEASE_BALANCE,
    ADMIN_CREDIT,
    ADMIN_DEBIT,
    WITHDRAWAL,
    WITHDRAWAL_CANCELLED,
    WITHDRAWAL_FAILED
}
