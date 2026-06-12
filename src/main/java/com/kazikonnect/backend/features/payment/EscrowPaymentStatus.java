package com.kazikonnect.backend.features.payment;

public enum EscrowPaymentStatus {
    PENDING,
    SUCCESS,
    ESCROWED,
    RELEASED,
    REFUNDED,
    FAILED,
    PARTIALLY_SETTLED,
    DISPUTED,
    // B2C specific statuses
    B2C_INITIATED,
    B2C_PENDING,
    B2C_RETRY_PENDING,
    B2C_FAILED,
    B2C_MAX_RETRIES_EXCEEDED;

    public boolean canTransitionTo(EscrowPaymentStatus target) {
        return switch (this) {
            case PENDING -> target == SUCCESS || target == ESCROWED || target == FAILED || target == REFUNDED ||
                    target == PARTIALLY_SETTLED || target == RELEASED || target == B2C_INITIATED;
            case SUCCESS -> target == ESCROWED || target == RELEASED || target == REFUNDED || target == FAILED ||
                    target == PARTIALLY_SETTLED || target == DISPUTED || target == B2C_INITIATED;
            case ESCROWED -> target == RELEASED || target == REFUNDED || target == FAILED ||
                    target == PARTIALLY_SETTLED || target == DISPUTED || target == B2C_INITIATED;
            case DISPUTED -> target == RELEASED || target == PARTIALLY_SETTLED || target == REFUNDED;
            case RELEASED -> false;
            case REFUNDED -> false;
            // FAILED can go back to PENDING when client retries payment
            case FAILED -> target == REFUNDED || target == PENDING;
            case PARTIALLY_SETTLED -> false;
            case B2C_INITIATED -> target == RELEASED || target == B2C_PENDING || target == B2C_RETRY_PENDING ||
                    target == B2C_FAILED || target == FAILED;
            case B2C_PENDING -> target == RELEASED || target == B2C_RETRY_PENDING || target == B2C_FAILED;
            case B2C_RETRY_PENDING ->
                target == B2C_INITIATED || target == B2C_FAILED || target == B2C_MAX_RETRIES_EXCEEDED;
            case B2C_FAILED -> target == B2C_RETRY_PENDING || target == REFUNDED || target == B2C_MAX_RETRIES_EXCEEDED;
            case B2C_MAX_RETRIES_EXCEEDED -> false;
        };
    }
}
