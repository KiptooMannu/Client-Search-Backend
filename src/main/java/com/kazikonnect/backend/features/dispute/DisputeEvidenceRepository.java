package com.kazikonnect.backend.features.dispute;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DisputeEvidenceRepository extends JpaRepository<DisputeEvidence, UUID> {

    // Find all evidence for a dispute
    List<DisputeEvidence> findByDisputeIdOrderByCreatedAtDesc(UUID disputeId);

    // Find admin-requested evidence
    List<DisputeEvidence> findByDisputeIdAndIsAdminRequestedTrueOrderByCreatedAtDesc(UUID disputeId);

    // Find evidence by type
    List<DisputeEvidence> findByDisputeIdAndFileTypeOrderByCreatedAtDesc(UUID disputeId, String fileType);

    // Count evidence for dispute
    long countByDisputeId(UUID disputeId);

    // Find evidence uploaded by user for a dispute
    List<DisputeEvidence> findByDisputeIdAndUploadedByIdOrderByCreatedAtDesc(UUID disputeId, UUID userId);
}
