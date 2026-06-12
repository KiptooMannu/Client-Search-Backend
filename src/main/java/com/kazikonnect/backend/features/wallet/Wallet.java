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
@Table(name = "wallets", indexes = {
        @Index(name = "idx_wallet_user", columnList = "user_id")
})
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Builder.Default
    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.NUMERIC)
    @Column(nullable = false)
    private Double balance = 0.0;

    @Builder.Default
    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.NUMERIC)
    @Column(name = "total_earned", nullable = false)
    private Double totalEarned = 0.0;

    @Builder.Default
    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.NUMERIC)
    @Column(name = "total_withdrawn", nullable = false)
    private Double totalWithdrawn = 0.0;

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
