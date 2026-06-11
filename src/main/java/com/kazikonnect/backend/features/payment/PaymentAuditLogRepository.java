package com.kazikonnect.backend.features.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.UUID;

public interface PaymentAuditLogRepository extends JpaRepository<PaymentAuditLog, UUID> {
    
    @Query("SELECT p FROM PaymentAuditLog p WHERE p.escrowPayment.id = :escrowPaymentId ORDER BY p.createdAt DESC")
    List<PaymentAuditLog> findByEscrowPaymentId(@Param("escrowPaymentId") UUID escrowPaymentId);
}
