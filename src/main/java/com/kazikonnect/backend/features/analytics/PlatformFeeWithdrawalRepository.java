package com.kazikonnect.backend.features.analytics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlatformFeeWithdrawalRepository extends JpaRepository<PlatformFeeWithdrawal, UUID> {

    List<PlatformFeeWithdrawal> findByStatus(WithdrawalStatus status);

    Optional<PlatformFeeWithdrawal> findTopByOrderByCreatedAtDesc();

    @Query("SELECT SUM(p.amount) FROM PlatformFeeWithdrawal p WHERE p.status = :status")
    Double sumAmountByStatus(@Param("status") WithdrawalStatus status);

    @Query("SELECT p FROM PlatformFeeWithdrawal p WHERE p.status = :status AND p.requestedAt < :threshold")
    List<PlatformFeeWithdrawal> findPendingWithdrawalsOlderThan(@Param("status") WithdrawalStatus status, @Param("threshold") LocalDateTime threshold);

    List<PlatformFeeWithdrawal> findByRequestedAtBetween(LocalDateTime startDate, LocalDateTime endDate);
}
