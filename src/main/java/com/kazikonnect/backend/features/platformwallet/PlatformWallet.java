package com.kazikonnect.backend.features.platformwallet;

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
@Table(name = "platform_wallets")
public class PlatformWallet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Builder.Default
    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.NUMERIC)
    @Column(nullable = false, precision = 14, scale = 2)
    private Double availableBalance = 0.0;

    @Builder.Default
    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.NUMERIC)
    @Column(name = "pending_balance", nullable = false, precision = 14, scale = 2)
    private Double pendingBalance = 0.0;

    @Builder.Default
    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.NUMERIC)
    @Column(name = "total_revenue", nullable = false, precision = 14, scale = 2)
    private Double totalRevenue = 0.0;

    @Builder.Default
    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.NUMERIC)
    @Column(name = "total_withdrawn", nullable = false, precision = 14, scale = 2)
    private Double totalWithdrawn = 0.0;

    @Builder.Default
    @Column(name = "total_transactions", nullable = false)
    private Long totalTransactions = 0L;

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
