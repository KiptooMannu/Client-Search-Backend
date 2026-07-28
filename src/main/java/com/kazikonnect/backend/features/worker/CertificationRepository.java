package com.kazikonnect.backend.features.worker;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.UUID;

public interface CertificationRepository extends JpaRepository<Certification, UUID> {
    java.util.List<Certification> findAllByWorkerId(UUID workerId);

    /** Bulk-delete all certifications for a worker directly via JPQL,
     *  bypassing Hibernate entity-lifecycle events to avoid stale-object conflicts. */
    @Modifying
    @Query("DELETE FROM Certification c WHERE c.worker.id = :workerId")
    void deleteAllByWorkerId(@Param("workerId") UUID workerId);

    void deleteByWorkerId(UUID workerId);
}
