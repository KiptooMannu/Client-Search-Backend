package com.nestfind.backend.features.worker;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface WorkHistoryRepository extends JpaRepository<WorkHistory, UUID> {
    java.util.List<WorkHistory> findAllByWorkerId(UUID workerId);
    void deleteByWorkerId(UUID workerId);
}
