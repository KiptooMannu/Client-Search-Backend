package com.kazikonnect.backend.features.dispute;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DisputeRepository extends JpaRepository<Dispute, UUID> {

    // Find by job request
    Optional<Dispute> findByJobRequestId(UUID jobRequestId);

    // Find by status
    List<Dispute> findByStatus(DisputeStatus status);

    // Find open disputes
    List<Dispute> findByStatusIn(List<DisputeStatus> statuses);

    // Find assigned to admin
    List<Dispute> findByAssignedToAdminIdAndStatus(UUID adminId, DisputeStatus status);

    // Find all for admin (pagination-friendly)
    List<Dispute> findByAssignedToAdminIdOrderByCreatedAtDesc(UUID adminId);

    // Find disputes needing assignment
    List<Dispute> findByAssignedToAdminIdIsNullOrderByCreatedAtDesc();

    // Find by priority
    List<Dispute> findByPriorityAndStatusInOrderByCreatedAtDesc(DisputePriority priority, List<DisputeStatus> statuses);

    // Find unresolved disputes (for escrow lock status)
    @Query("SELECT d FROM Dispute d WHERE d.status NOT IN ('RESOLVED', 'CLOSED') AND d.jobRequest.id = :jobId")
    Optional<Dispute> findUnresolvedByJobId(@Param("jobId") UUID jobId);

    // Count open disputes
    long countByStatusIn(List<DisputeStatus> statuses);

    // Find recently created disputes
    List<Dispute> findByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime dateTime);

    // Find disputes filed by user
    List<Dispute> findByFiledByIdOrderByCreatedAtDesc(UUID userId);

    // Find disputes involving user (filed by or assigned worker)
    @Query("SELECT d FROM Dispute d WHERE d.filedBy.id = :userId OR d.jobRequest.worker.user.id = :userId ORDER BY d.createdAt DESC")
    List<Dispute> findDisputesInvolvingUser(@Param("userId") UUID userId);

    // Find disputes awaiting evidence
    List<Dispute> findByStatusOrderByEvidenceRequestedAtAsc(DisputeStatus status);

    // Count disputes by status
    long countByStatusAndCreatedAtAfter(DisputeStatus status, LocalDateTime dateTime);
}
