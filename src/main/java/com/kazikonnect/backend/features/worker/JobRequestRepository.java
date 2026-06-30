package com.kazikonnect.backend.features.worker;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface JobRequestRepository extends JpaRepository<JobRequest, UUID> {
    List<JobRequest> findAllByClientId(UUID clientId);
    List<JobRequest> findAllByWorkerId(UUID workerId);
    List<JobRequest> findAllByStatus(JobStatus status);

    @Query("SELECT j FROM JobRequest j WHERE " +
           "(j.status = com.kazikonnect.backend.features.worker.JobStatus.ACCEPTED OR j.status = com.kazikonnect.backend.features.worker.JobStatus.AWAITING_FUNDING) " +
           "AND j.escrowFunded = false " +
           "AND j.createdAt < :expiryThreshold")
    List<JobRequest> findExpiredJobs(@Param("expiryThreshold") LocalDateTime expiryThreshold);
}
