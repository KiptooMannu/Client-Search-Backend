package com.kazikonnect.backend.features.worker;

import com.kazikonnect.backend.features.auth.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    void deleteByUserId(UUID userId);

    // OPTIMIZED Marketplace search 
    @Query("SELECT DISTINCT w FROM WorkerProfile w " +
           "LEFT JOIN FETCH w.user u " +
           "LEFT JOIN FETCH w.skills s " +
           "WHERE w.isVisible = true AND w.status = :status " +
           "AND (:skill IS NULL OR s.name = :skill OR s.name LIKE %:skill%) " +
           "AND (:location IS NULL OR w.location = :location OR w.location LIKE %:location%) " +
           "AND (:minExp IS NULL OR w.experienceYears >= :minExp)")
    List<WorkerProfile> advancedSearch(
        @Param("status") WorkerStatus status,
        @Param("skill") String skill,
        @Param("location") String location,
        @Param("minExp") Integer minExp);

    // PAGINATED version for better performance
    @Query("SELECT DISTINCT w FROM WorkerProfile w " +
           "LEFT JOIN FETCH w.user u " +
           "LEFT JOIN FETCH w.skills s " +
           "WHERE w.isVisible = true AND w.status = :status " +
           "AND (:skill IS NULL OR s.name = :skill OR s.name LIKE %:skill%) " +
           "AND (:location IS NULL OR w.location = :location OR w.location LIKE %:location%) " +
           "AND (:minExp IS NULL OR w.experienceYears >= :minExp)")
    Page<WorkerProfile> advancedSearchPaged(
        @Param("status") WorkerStatus status,
        @Param("skill") String skill,
        @Param("location") String location,
        @Param("minExp") Integer minExp,
        Pageable pageable);

    // OPTIMIZED: Separate query for reviews to avoid cartesian product
    @Query("SELECT w FROM WorkerProfile w " +
           "LEFT JOIN FETCH w.user u " +
           "WHERE w.isVisible = true AND w.status = :status")
    List<WorkerProfile> findAllForMarketplace(@Param("status") WorkerStatus status);

    // Batch fetch reviews separately
    @Query("SELECT w FROM WorkerProfile w " +
           "LEFT JOIN FETCH w.reviews r " +
           "WHERE w IN :workers")
    List<WorkerProfile> fetchReviewsBatch(@Param("workers") List<WorkerProfile> workers);

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
    long countByStatus(WorkerStatus status);

    @Query("SELECT DISTINCT w.location FROM WorkerProfile w " +
           "WHERE w.isVisible = true AND w.status = com.kazikonnect.backend.features.worker.WorkerStatus.APPROVED " +
           "AND w.location IS NOT NULL AND w.location != ''")
    List<String> findDistinctLocations();
}