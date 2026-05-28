package com.kazikonnect.backend.features.payment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EscrowPaymentStatusTests {

    @Test
    void pendingCanTransitionToEscrowedAndFailedAndRefunded() {
        assertTrue(EscrowPaymentStatus.PENDING.canTransitionTo(EscrowPaymentStatus.ESCROWED));
        assertTrue(EscrowPaymentStatus.PENDING.canTransitionTo(EscrowPaymentStatus.FAILED));
        assertTrue(EscrowPaymentStatus.PENDING.canTransitionTo(EscrowPaymentStatus.REFUNDED));
    }

    @Test
    void successCanTransitionToEscrowedReleasedOrRefunded() {
        assertTrue(EscrowPaymentStatus.SUCCESS.canTransitionTo(EscrowPaymentStatus.ESCROWED));
        assertTrue(EscrowPaymentStatus.SUCCESS.canTransitionTo(EscrowPaymentStatus.RELEASED));
        assertTrue(EscrowPaymentStatus.SUCCESS.canTransitionTo(EscrowPaymentStatus.REFUNDED));
    }

    @Test
    void escrowedCanTransitionToReleasedOrRefundedOrFailed() {
        assertTrue(EscrowPaymentStatus.ESCROWED.canTransitionTo(EscrowPaymentStatus.RELEASED));
        assertTrue(EscrowPaymentStatus.ESCROWED.canTransitionTo(EscrowPaymentStatus.REFUNDED));
        assertTrue(EscrowPaymentStatus.ESCROWED.canTransitionTo(EscrowPaymentStatus.FAILED));
    }

    @Test
    void releasedCannotTransition() {
        assertFalse(EscrowPaymentStatus.RELEASED.canTransitionTo(EscrowPaymentStatus.REFUNDED));
    }
}
