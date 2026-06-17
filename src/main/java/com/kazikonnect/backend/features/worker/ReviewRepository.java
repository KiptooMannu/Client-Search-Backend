package com.kazikonnect.backend.features.worker;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {
    List<Review> findAllByWorkerId(UUID workerId);
    List<Review> findAllByClientId(UUID clientId);
    Optional<Review> findByJobRequestId(UUID jobId);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.worker.id = :workerId")
    Optional<Double> findAverageRatingByWorkerId(@Param("workerId") UUID workerId);

    @Query("SELECT COUNT(r) FROM Review r WHERE r.worker.id = :workerId")
    Long countReviewsByWorkerId(@Param("workerId") UUID workerId);

    @Query("SELECT r FROM Review r WHERE r.worker.id = :workerId ORDER BY r.createdAt DESC")
    List<Review> findRecentReviewsByWorkerId(@Param("workerId") UUID workerId);
}

