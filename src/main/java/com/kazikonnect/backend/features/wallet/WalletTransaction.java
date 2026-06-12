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
@Table(name = "wallet_transactions", indexes = {
        @Index(name = "idx_txn_user", columnList = "user_id"),
        @Index(name = "idx_txn_reference", columnList = "reference_id"),
        @Index(name = "idx_txn_type", columnList = "txn_type"),
        @Index(name = "idx_txn_created", columnList = "created_at")
})
public class WalletTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "txn_type", nullable = false)
    private WalletTransactionType txnType;

    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.NUMERIC)
    @Column(nullable = false)
    private Double amount;

    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.NUMERIC)
    @Column(name = "balance_after", nullable = false)
    private Double balanceAfter;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(length = 300)
    private String description;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
