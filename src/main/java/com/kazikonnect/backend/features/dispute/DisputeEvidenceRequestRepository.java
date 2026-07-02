package com.kazikonnect.backend.features.dispute;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface DisputeEvidenceRequestRepository extends JpaRepository<DisputeEvidenceRequest, UUID> {

    // Find all evidence requests for a dispute
    List<DisputeEvidenceRequest> findByDisputeIdOrderByCreatedAtDesc(UUID disputeId);

    // Find pending evidence requests
    List<DisputeEvidenceRequest> findByDisputeIdAndRequestStatusOrderByCreatedAtAsc(UUID disputeId, EvidenceRequestStatus status);

    // Find evidence requests for a user
    List<DisputeEvidenceRequest> findByRequestedFromUserIdAndRequestStatusOrderByCreatedAtDesc(UUID userId, EvidenceRequestStatus status);

    // Find overdue evidence requests
    List<DisputeEvidenceRequest> findByRequestStatusAndDueDateBeforeOrderByDueDateAsc(EvidenceRequestStatus status, LocalDateTime dateTime);

    // Find pending evidence requests from admin
    List<DisputeEvidenceRequest> findByRequestedByAdminIdAndRequestStatusOrderByCreatedAtDesc(UUID adminId, EvidenceRequestStatus status);

    // Find all evidence requests for a dispute
    List<DisputeEvidenceRequest> findByDisputeId(UUID disputeId);

    // Find pending evidence requests for a specific dispute and user
    List<DisputeEvidenceRequest> findByDisputeIdAndRequestedFromUserIdAndRequestStatus(UUID disputeId, UUID userId, EvidenceRequestStatus status);

    // Count pending requests for dispute
    long countByDisputeIdAndRequestStatus(UUID disputeId, EvidenceRequestStatus status);

    // Delete evidence requests by admin id
    void deleteByRequestedByAdminId(UUID adminId);

    // Find evidence requests not hidden from admin
    List<DisputeEvidenceRequest> findByDisputeIdAndHiddenFromAdminFalseOrderByCreatedAtDesc(UUID disputeId);

    // Find evidence requests not hidden from user
    List<DisputeEvidenceRequest> findByDisputeIdAndHiddenFromUserFalseOrderByCreatedAtDesc(UUID disputeId);

    // Find evidence requests directed at a specific user and not hidden from user
    List<DisputeEvidenceRequest> findByDisputeIdAndRequestedFromUserIdAndHiddenFromUserFalseOrderByCreatedAtDesc(UUID disputeId, UUID userId);
}
