package com.kazikonnect.backend.features.settlementwallet;

import com.kazikonnect.backend.features.auth.User;
import com.kazikonnect.backend.features.platformwallet.WithdrawalMethod;
import com.kazikonnect.backend.features.platformwallet.WithdrawalStatus;
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
@Table(name = "settlement_withdrawals", indexes = {
        @Index(name = "idx_settlement_withdrawal_wallet", columnList = "wallet_id"),
        @Index(name = "idx_settlement_withdrawal_status", columnList = "status"),
        @Index(name = "idx_settlement_withdrawal_timestamp", columnList = "created_at")
})
public class SettlementWithdrawal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false)
    private ClientSettlementWallet wallet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by", nullable = false)
    private User requestedBy;

    @Column(name = "withdrawal_reference", nullable = false, unique = true)
    private String withdrawalReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "withdrawal_method", nullable = false)
    private WithdrawalMethod withdrawalMethod;

    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.NUMERIC)
    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private Double amount;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "account_name")
    private String accountName;

    @Column(name = "account_number")
    private String accountNumber;

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "bank_branch")
    private String bankBranch;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private WithdrawalStatus status;

    @Column(name = "receipt_number")
    private String receiptNumber;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "mpesa_conversation_id")
    private String mpesaConversationId;

    @Column(name = "mpesa_originator_conversation_id")
    private String mpesaOriginatorConversationId;

    @Column(name = "mpesa_transaction_id")
    private String mpesaTransactionId;

    @Column(name = "mpesa_initiated_at")
    private LocalDateTime mpesaInitiatedAt;

    @Column(name = "mpesa_completed_at")
    private LocalDateTime mpesaCompletedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
