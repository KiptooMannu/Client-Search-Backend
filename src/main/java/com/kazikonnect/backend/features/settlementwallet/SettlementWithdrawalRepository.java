package com.kazikonnect.backend.features.settlementwallet;

import com.kazikonnect.backend.features.platformwallet.WithdrawalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SettlementWithdrawalRepository extends JpaRepository<SettlementWithdrawal, UUID> {
    
    Optional<SettlementWithdrawal> findByWithdrawalReference(String withdrawalReference);
    
    List<SettlementWithdrawal> findByWalletId(UUID walletId);
    
    List<SettlementWithdrawal> findByWallet_User_Id(UUID userId);
    
    List<SettlementWithdrawal> findByStatus(WithdrawalStatus status);
    
    List<SettlementWithdrawal> findByRequestedBy_Id(UUID userId);
    
    @Query("SELECT w FROM SettlementWithdrawal w WHERE w.wallet.user.id = :userId ORDER BY w.createdAt DESC")
    Page<SettlementWithdrawal> findByUserIdOrderByCreatedAtDesc(@Param("userId") UUID userId, Pageable pageable);
    
    @Query("SELECT w FROM SettlementWithdrawal w WHERE w.wallet.user.id = :userId AND w.createdAt BETWEEN :startDate AND :endDate ORDER BY w.createdAt DESC")
    List<SettlementWithdrawal> findByUserIdAndDateRange(@Param("userId") UUID userId, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT SUM(w.amount) FROM SettlementWithdrawal w WHERE w.wallet.user.id = :userId AND w.status = :status AND w.createdAt BETWEEN :startDate AND :endDate")
    Double sumByUserIdAndStatusAndDateRange(@Param("userId") UUID userId, @Param("status") WithdrawalStatus status, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
}
