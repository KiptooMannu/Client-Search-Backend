package com.nestfind.backend.features.worker;

import com.nestfind.backend.features.auth.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkerProfileRepository extends JpaRepository<WorkerProfile, UUID> {
    Optional<WorkerProfile> findByUserId(UUID userId);
    Optional<WorkerProfile> findByUserEmail(String email);
    Optional<WorkerProfile> findByUser(User user);
    boolean existsByUser(User user);

    // Marketplace search — only visible/approved workers
    @Query("SELECT DISTINCT w FROM WorkerProfile w LEFT JOIN FETCH w.skills s " +
           "WHERE w.isVisible = true AND w.status = :status " +
           "AND (:skill IS NULL OR s.name LIKE %:skill%) " +
           "AND (:location IS NULL OR w.location LIKE %:location%) " +
           "AND (:minExp IS NULL OR w.experienceYears >= :minExp)")
    List<WorkerProfile> advancedSearch(
        @Param("status") WorkerStatus status,
        @Param("skill") String skill,
        @Param("location") String location,
        @Param("minExp") Integer minExp
    );

    // Fetch by status with all collections eagerly to avoid N+1
    @Query("SELECT DISTINCT w FROM WorkerProfile w " +
           "LEFT JOIN FETCH w.user " +
           "WHERE w.status = :status")
    List<WorkerProfile> findAllByStatusWithDetails(@Param("status") WorkerStatus status);

    // Fetch all with collections to avoid N+1
    @Query("SELECT DISTINCT w FROM WorkerProfile w " +
           "LEFT JOIN FETCH w.user")
    List<WorkerProfile> findAllWithDetails();

    List<WorkerProfile> findAllByStatus(WorkerStatus status);
}
