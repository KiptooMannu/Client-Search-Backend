package com.kazikonnect.backend.features.dispute;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface DisputeAuditTrailRepository extends JpaRepository<DisputeAuditTrail, UUID> {

    // Find all audit trail entries for a dispute
    List<DisputeAuditTrail> findByDisputeIdOrderByCreatedAtDesc(UUID disputeId);

    // Find by action type
    List<DisputeAuditTrail> findByDisputeIdAndActionTypeOrderByCreatedAtDesc(UUID disputeId, String actionType);

    // Find audit trail by actor
    List<DisputeAuditTrail> findByActorIdOrderByCreatedAtDesc(UUID actorId);

    // Find recent audit trail entries
    List<DisputeAuditTrail> findByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime dateTime);

    // Count actions for dispute
    long countByDisputeId(UUID disputeId);

    // Find specific action type for dispute
    List<DisputeAuditTrail> findByDisputeIdAndActionTypeIn(UUID disputeId, List<String> actionTypes);
}
