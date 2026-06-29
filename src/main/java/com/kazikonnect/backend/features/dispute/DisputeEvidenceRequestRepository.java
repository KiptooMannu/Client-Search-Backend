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

    // Count pending requests for dispute
    long countByDisputeIdAndRequestStatus(UUID disputeId, EvidenceRequestStatus status);
}
