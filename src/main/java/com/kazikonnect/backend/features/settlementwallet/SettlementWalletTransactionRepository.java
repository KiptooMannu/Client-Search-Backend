package com.kazikonnect.backend.features.settlementwallet;

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
public interface SettlementWalletTransactionRepository extends JpaRepository<SettlementWalletTransaction, UUID> {
    
    Optional<SettlementWalletTransaction> findByTransactionReference(String transactionReference);
    
    List<SettlementWalletTransaction> findByWalletId(UUID walletId);
    
    List<SettlementWalletTransaction> findByWallet_User_Id(UUID userId);
    
    List<SettlementWalletTransaction> findByBookingId(UUID bookingId);
    
    List<SettlementWalletTransaction> findByEscrowId(UUID escrowId);
    
    List<SettlementWalletTransaction> findByTransactionType(SettlementTransactionType transactionType);
    
    @Query("SELECT t FROM SettlementWalletTransaction t WHERE t.wallet.user.id = :userId AND t.timestamp BETWEEN :startDate AND :endDate ORDER BY t.timestamp DESC")
    List<SettlementWalletTransaction> findByUserIdAndDateRange(@Param("userId") UUID userId, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT t FROM SettlementWalletTransaction t WHERE t.wallet.id = :walletId ORDER BY t.timestamp DESC")
    Page<SettlementWalletTransaction> findByWalletIdOrderByTimestampDesc(@Param("walletId") UUID walletId, Pageable pageable);
    
    @Query("SELECT SUM(t.amount) FROM SettlementWalletTransaction t WHERE t.wallet.user.id = :userId AND t.transactionType = :type AND t.timestamp BETWEEN :startDate AND :endDate")
    Double sumByUserIdAndTypeAndDateRange(@Param("userId") UUID userId, @Param("type") SettlementTransactionType type, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
}
