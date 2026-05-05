package com.kazikonnect.backend.features.worker;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {
    List<Review> findAllByWorkerId(UUID workerId);
    List<Review> findAllByClientId(UUID clientId);
}
