package com.kazikonnect.backend.features.worker;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.UUID;

public interface WorkHistoryRepository extends JpaRepository<WorkHistory, UUID> {
    java.util.List<WorkHistory> findAllByWorkerId(UUID workerId);

    /** Bulk-delete all work history entries for a worker directly via JPQL,
     *  bypassing Hibernate entity-lifecycle events to avoid stale-object conflicts. */
    @Modifying
    @Query("DELETE FROM WorkHistory w WHERE w.worker.id = :workerId")
    void deleteAllByWorkerId(@Param("workerId") UUID workerId);

    void deleteByWorkerId(UUID workerId);
}
