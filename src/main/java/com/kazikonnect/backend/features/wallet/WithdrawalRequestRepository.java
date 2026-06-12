package com.kazikonnect.backend.features.wallet;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WithdrawalRequestRepository extends JpaRepository<WithdrawalRequest, UUID> {

    List<WithdrawalRequest> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<WithdrawalRequest> findByB2cConversationId(String conversationId);

    List<WithdrawalRequest> findByStatusAndB2cNextRetryAtBefore(String status, LocalDateTime nextRetryAt);

    List<WithdrawalRequest> findByStatus(String status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM WithdrawalRequest w WHERE w.b2cConversationId = :conversationId")
    Optional<WithdrawalRequest> findByB2cConversationIdForUpdate(@Param("conversationId") String conversationId);
}
