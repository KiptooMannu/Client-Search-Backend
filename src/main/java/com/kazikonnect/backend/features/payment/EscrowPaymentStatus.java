package com.kazikonnect.backend.features.payment;

public enum EscrowPaymentStatus {
    PENDING,
    SUCCESS,
    ESCROWED,
    RELEASED,
    REFUNDED,
    FAILED,
    PARTIALLY_SETTLED,
    DISPUTED;

    public boolean canTransitionTo(EscrowPaymentStatus target) {
        return switch (this) {
            case PENDING -> target == SUCCESS || target == ESCROWED || target == FAILED || target == REFUNDED || target == PARTIALLY_SETTLED || target == RELEASED;
            case SUCCESS -> target == ESCROWED || target == RELEASED || target == REFUNDED || target == FAILED || target == PARTIALLY_SETTLED || target == DISPUTED;
            case ESCROWED -> target == RELEASED || target == REFUNDED || target == FAILED || target == PARTIALLY_SETTLED || target == DISPUTED;
            case DISPUTED -> target == RELEASED || target == PARTIALLY_SETTLED || target == REFUNDED;
            case RELEASED -> false;
            case REFUNDED -> false;
            case FAILED -> target == REFUNDED;
            case PARTIALLY_SETTLED -> false;
        };
    }
}
