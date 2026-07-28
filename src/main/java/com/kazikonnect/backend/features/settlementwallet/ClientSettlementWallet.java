package com.kazikonnect.backend.features.settlementwallet;

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
@Table(name = "client_settlement_wallets", indexes = {
        @Index(name = "idx_settlement_wallet_user", columnList = "user_id"),
        @Index(name = "idx_settlement_wallet_frozen", columnList = "is_frozen")
})
public class ClientSettlementWallet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Builder.Default
    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.NUMERIC)
    @Column(nullable = false, precision = 14, scale = 2)
    private Double availableBalance = 0.0;

    @Builder.Default
    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.NUMERIC)
    @Column(name = "pending_credits", nullable = false, precision = 14, scale = 2)
    private Double pendingCredits = 0.0;

    @Builder.Default
    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.NUMERIC)
    @Column(name = "total_refunded", nullable = false, precision = 14, scale = 2)
    private Double totalRefunded = 0.0;

    @Builder.Default
    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.NUMERIC)
    @Column(name = "total_withdrawn", nullable = false, precision = 14, scale = 2)
    private Double totalWithdrawn = 0.0;

    @Builder.Default
    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.NUMERIC)
    @Column(name = "total_settlement_credits", nullable = false, precision = 14, scale = 2)
    private Double totalSettlementCredits = 0.0;

    @Builder.Default
    @Column(name = "is_frozen", nullable = false)
    private Boolean isFrozen = false;

    @Column(name = "freeze_reason")
    private String freezeReason;

    @Column(name = "frozen_at")
    private LocalDateTime frozenAt;

    @Column(name = "unfrozen_at")
    private LocalDateTime unfrozenAt;

    @Column(name = "created_at")
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
