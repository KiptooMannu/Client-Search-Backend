package com.kazikonnect.backend.features.payment;

import com.kazikonnect.backend.features.worker.JobRequest;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "escrow_payments", indexes = {
        @Index(name = "idx_escrow_job", columnList = "job_request_id"),
        @Index(name = "idx_escrow_checkout", columnList = "checkout_request_id")
})
public class EscrowPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    private Long version;

    @Transient
    private EscrowPaymentStatus previousStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_request_id", nullable = false)
    private JobRequest jobRequest;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EscrowPaymentStatus status;

    @Column(name = "amount", nullable = false)
    private Double amount;

    @Column(name = "client_phone_number", nullable = false)
    private String phoneNumber;

    @Column(name = "checkout_request_id", unique = true)
    private String checkoutRequestId;

    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "mpesa_receipt_number")
    private String mpesaReceiptNumber;

    @Column(name = "transaction_date")
    private LocalDateTime transactionDate;

    @Column(name = "platform_fee")
    private Double platformFee;

    @Column(name = "worker_amount")
    private Double workerAmount;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(name = "timeout_at")
    private LocalDateTime timeoutAt;

    // B2C Payout tracking fields
    @Column(name = "b2c_conversation_id")
    private String b2cConversationId;

    @Column(name = "b2c_originator_conversation_id")
    private String b2cOriginatorConversationId;

    @Column(name = "b2c_transaction_id")
    private String b2cTransactionId;

    @Column(name = "b2c_initiated_at")
    private LocalDateTime b2cInitiatedAt;

    @Column(name = "b2c_completed_at")
    private LocalDateTime b2cCompletedAt;

    @Column(name = "b2c_retry_count")
    private Integer b2cRetryCount;

    @Column(name = "b2c_next_retry_at")
    private LocalDateTime b2cNextRetryAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PostLoad
    protected void onLoad() {
        previousStatus = status;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        if (previousStatus != null && status != previousStatus) {
            validateStatusTransition(previousStatus, status);
        }
        updatedAt = LocalDateTime.now();
    }

    private void validateStatusTransition(EscrowPaymentStatus from, EscrowPaymentStatus to) {
        if (from == to) {
            return;
        }
        if (!from.canTransitionTo(to)) {
            throw new IllegalStateException(
                    String.format("Invalid escrow status transition from %s to %s", from, to));
        }
    }
}
