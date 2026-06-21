package com.kazikonnect.backend.features.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface EscrowPaymentRepository extends JpaRepository<EscrowPayment, UUID> {

    Optional<EscrowPayment> findTopByJobRequestIdOrderByCreatedAtDesc(UUID jobRequestId);

    Optional<EscrowPayment> findTopByJobRequestIdAndStatusInOrderByCreatedAtDesc(UUID jobRequestId, List<EscrowPaymentStatus> statuses);

    Optional<EscrowPayment> findByCheckoutRequestId(String checkoutRequestId);

    List<EscrowPayment> findAllByStatusAndTimeoutAtBefore(EscrowPaymentStatus status, LocalDateTime timeoutAt);

    // B2C specific queries
    Optional<EscrowPayment> findByB2cConversationId(String b2cConversationId);

    Optional<EscrowPayment> findByMpesaReceiptNumber(String mpesaReceiptNumber);

    List<EscrowPayment> findByStatusAndB2cNextRetryAtBefore(EscrowPaymentStatus status, LocalDateTime nextRetryAt);

    List<EscrowPayment> findByStatus(EscrowPaymentStatus status);

    List<EscrowPayment> findByStatusAndTimeoutAtBefore(EscrowPaymentStatus status, LocalDateTime timeoutAt);

    long countByStatusAndUpdatedAtBefore(EscrowPaymentStatus status, LocalDateTime updatedAt);

    List<EscrowPayment> findByStatusAndUpdatedAtBefore(EscrowPaymentStatus status, LocalDateTime updatedAt);

    long countByPhoneNumberAndCreatedAtAfter(String phoneNumber, LocalDateTime createdAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM EscrowPayment e WHERE e.checkoutRequestId = :checkoutRequestId")
    Optional<EscrowPayment> findByCheckoutRequestIdForUpdate(@Param("checkoutRequestId") String checkoutRequestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM EscrowPayment e WHERE e.b2cConversationId = :conversationId")
    Optional<EscrowPayment> findByB2cConversationIdForUpdate(@Param("conversationId") String conversationId);
}
