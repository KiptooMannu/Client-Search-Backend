package com.kazikonnect.backend.features.platformwallet;

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
public interface PlatformWalletLedgerRepository extends JpaRepository<PlatformWalletLedger, UUID> {
    
    Optional<PlatformWalletLedger> findByTransactionReference(String transactionReference);
    
    List<PlatformWalletLedger> findByBookingId(UUID bookingId);
    
    List<PlatformWalletLedger> findByEscrowId(UUID escrowId);
    
    @Query("SELECT l FROM PlatformWalletLedger l WHERE l.timestamp BETWEEN :startDate AND :endDate ORDER BY l.timestamp DESC")
    List<PlatformWalletLedger> findByDateRange(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT l FROM PlatformWalletLedger l ORDER BY l.timestamp DESC")
    Page<PlatformWalletLedger> findAllOrderByTimestampDesc(Pageable pageable);
    
    @Query("SELECT SUM(l.platformFeeAmount) FROM PlatformWalletLedger l WHERE l.timestamp BETWEEN :startDate AND :endDate")
    Double sumRevenueByDateRange(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT COUNT(l) FROM PlatformWalletLedger l WHERE l.timestamp BETWEEN :startDate AND :endDate")
    Long countTransactionsByDateRange(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
}
