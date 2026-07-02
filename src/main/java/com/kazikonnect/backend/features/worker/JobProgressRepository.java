package com.kazikonnect.backend.features.worker;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface JobProgressRepository extends JpaRepository<JobProgress, UUID> {
    List<JobProgress> findByJobRequestIdOrderByCreatedAtAsc(UUID jobRequestId);
}
