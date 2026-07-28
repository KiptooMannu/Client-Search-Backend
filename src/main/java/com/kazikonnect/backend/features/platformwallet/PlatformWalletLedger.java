package com.kazikonnect.backend.features.platformwallet;

import com.kazikonnect.backend.features.auth.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "platform_wallet_ledger", indexes = {
        @Index(name = "idx_platform_ledger_booking", columnList = "booking_id"),
        @Index(name = "idx_platform_ledger_escrow", columnList = "escrow_id"),
        @Index(name = "idx_platform_ledger_timestamp", columnList = "timestamp")
})
public class PlatformWalletLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "transaction_reference", nullable = false, unique = true)
    private String transactionReference;

    @Column(name = "booking_id")
    private UUID bookingId;

    @Column(name = "escrow_id")
    private UUID escrowId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private User client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_id")
    private User worker;

    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.NUMERIC)
    @Column(name = "total_job_amount", precision = 14, scale = 2)
    private Double totalJobAmount;

    @JdbcTypeCode(Types.NUMERIC)
    @Column(name = "platform_fee_percent", precision = 5, scale = 2)
    private Double platformFeePercent;

    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.NUMERIC)
    @Column(name = "platform_fee_amount", precision = 14, scale = 2)
    private Double platformFeeAmount;

    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.NUMERIC)
    @Column(name = "worker_payout", precision = 14, scale = 2)
    private Double workerPayout;

    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.NUMERIC)
    @Column(name = "balance_before", precision = 14, scale = 2)
    private Double balanceBefore;

    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.NUMERIC)
    @Column(name = "balance_after", precision = 14, scale = 2)
    private Double balanceAfter;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    private PlatformTransactionType transactionType;

    @Column(columnDefinition = "TEXT")
    private String description;

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
