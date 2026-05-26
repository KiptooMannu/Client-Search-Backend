package com.kazikonnect.backend.features.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface EscrowPaymentRepository extends JpaRepository<EscrowPayment, UUID> {

    Optional<EscrowPayment> findByJobRequestId(UUID jobRequestId);

    Optional<EscrowPayment> findByCheckoutRequestId(String checkoutRequestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM EscrowPayment e WHERE e.checkoutRequestId = :checkoutRequestId")
    Optional<EscrowPayment> findByCheckoutRequestIdForUpdate(@Param("checkoutRequestId") String checkoutRequestId);
}
