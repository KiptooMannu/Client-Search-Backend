package com.kazikonnect.backend.features.payment;

public enum EscrowPaymentStatus {
    PENDING,
    SUCCESS,
    ESCROWED,
    RELEASED,
    REFUNDED,
    FAILED;

    public boolean canTransitionTo(EscrowPaymentStatus target) {
        return switch (this) {
            case PENDING -> target == SUCCESS || target == ESCROWED || target == FAILED || target == REFUNDED;
            case SUCCESS -> target == RELEASED || target == REFUNDED || target == FAILED;
            case ESCROWED -> target == RELEASED || target == REFUNDED || target == FAILED;
            case RELEASED -> false;
            case REFUNDED -> false;
            case FAILED -> target == REFUNDED;
        };
    }
}
