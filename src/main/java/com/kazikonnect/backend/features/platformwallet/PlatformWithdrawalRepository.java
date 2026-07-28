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
public interface PlatformWithdrawalRepository extends JpaRepository<PlatformWithdrawal, UUID> {
    
    Optional<PlatformWithdrawal> findByWithdrawalReference(String withdrawalReference);
    
    List<PlatformWithdrawal> findByStatus(WithdrawalStatus status);
    
    List<PlatformWithdrawal> findByRequestedBy_Id(UUID userId);
    
    @Query("SELECT w FROM PlatformWithdrawal w WHERE w.createdAt BETWEEN :startDate AND :endDate ORDER BY w.createdAt DESC")
    List<PlatformWithdrawal> findByDateRange(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT w FROM PlatformWithdrawal w ORDER BY w.createdAt DESC")
    Page<PlatformWithdrawal> findAllOrderByCreatedAtDesc(Pageable pageable);
    
    @Query("SELECT SUM(w.amount) FROM PlatformWithdrawal w WHERE w.status = :status AND w.createdAt BETWEEN :startDate AND :endDate")
    Double sumWithdrawnByStatusAndDateRange(@Param("status") WithdrawalStatus status, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
}
