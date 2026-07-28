package com.kazikonnect.backend.features.settlementwallet;

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
@Table(name = "settlement_wallet_transactions", indexes = {
        @Index(name = "idx_settlement_txn_wallet", columnList = "wallet_id"),
        @Index(name = "idx_settlement_txn_booking", columnList = "booking_id"),
        @Index(name = "idx_settlement_txn_escrow", columnList = "escrow_id"),
        @Index(name = "idx_settlement_txn_type", columnList = "transaction_type"),
        @Index(name = "idx_settlement_txn_timestamp", columnList = "timestamp")
})
public class SettlementWalletTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false)
    private ClientSettlementWallet wallet;

    @Column(name = "transaction_reference", nullable = false, unique = true)
    private String transactionReference;

    @Column(name = "booking_id")
    private UUID bookingId;

    @Column(name = "escrow_id")
    private UUID escrowId;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    private SettlementTransactionType transactionType;

    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.NUMERIC)
    @Column(name = "amount", nullable = false, precision = 14, scale = 2)
    private Double amount;

    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.NUMERIC)
    @Column(name = "balance_before", precision = 14, scale = 2)
    private Double balanceBefore;

    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.NUMERIC)
    @Column(name = "balance_after", precision = 14, scale = 2)
    private Double balanceAfter;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TransactionStatus status;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
        createdAt = LocalDateTime.now();
    }
}
