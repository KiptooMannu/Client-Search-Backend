package com.kazikonnect.backend.features.worker;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface JobRequestRepository extends JpaRepository<JobRequest, UUID> {
    List<JobRequest> findAllByClientId(UUID clientId);
    List<JobRequest> findAllByWorkerId(UUID workerId);
    List<JobRequest> findAllByStatus(JobStatus status);
}
