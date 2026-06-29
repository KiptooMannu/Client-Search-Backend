package com.kazikonnect.backend.features.dispute;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DisputeMessageRepository extends JpaRepository<DisputeMessage, UUID> {

    // Find all messages for a dispute
    List<DisputeMessage> findByDisputeIdOrderByCreatedAtAsc(UUID disputeId);

    // Find messages excluding admin-only notes
    List<DisputeMessage> findByDisputeIdAndIsAdminOnlyFalseOrderByCreatedAtAsc(UUID disputeId);

    // Find only admin notes
    List<DisputeMessage> findByDisputeIdAndIsAdminOnlyTrueOrderByCreatedAtDesc(UUID disputeId);

    // Find messages by type
    List<DisputeMessage> findByDisputeIdAndMessageTypeOrderByCreatedAtDesc(UUID disputeId, MessageType messageType);

    // Count unread messages for user
    long countByDisputeIdAndIsReadByClientFalseAndSenderIdNot(UUID disputeId, UUID userId);

    long countByDisputeIdAndIsReadByWorkerFalseAndSenderIdNot(UUID disputeId, UUID userId);

    long countByDisputeIdAndIsReadByAdminFalseAndSenderIdNot(UUID disputeId, UUID userId);
}
