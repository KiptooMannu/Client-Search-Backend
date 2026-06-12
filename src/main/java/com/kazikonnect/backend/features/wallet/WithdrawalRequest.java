package com.kazikonnect.backend.features.wallet;

import com.kazikonnect.backend.features.auth.User;
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
@Table(name = "withdrawal_requests", indexes = {
    @Index(name = "idx_withdrawals_user", columnList = "user_id"),
    @Index(name = "idx_withdrawals_status", columnList = "status")
})
public class WithdrawalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "amount", nullable = false)
    private Double amount;

    @Column(name = "phone_number", nullable = false)
    private String phoneNumber;

    @Column(name = "status", nullable = false)
    private String status; // PENDING, APPROVED, REJECTED, FAILED, B2C_INITIATED, B2C_RETRY_PENDING, B2C_FAILED, B2C_MAX_RETRIES_EXCEEDED

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

    @Builder.Default
    @Column(name = "b2c_retry_count")
    private Integer b2cRetryCount = 0;

    @Column(name = "b2c_next_retry_at")
    private LocalDateTime b2cNextRetryAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (b2cRetryCount == null) {
            b2cRetryCount = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
