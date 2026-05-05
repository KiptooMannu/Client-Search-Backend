package com.kazikonnect.backend.features.worker;

import java.time.LocalDateTime;
import java.util.UUID;

public record ReviewDTO(
    UUID id,
    UUID clientId,
    String clientName,
    UUID workerId,
    String workerName,
    Integer rating,
    String comment,
    LocalDateTime createdAt
) {
    public static ReviewDTO from(Review r) {
        return new ReviewDTO(
            r.getId(),
            r.getClient() != null ? r.getClient().getId() : null,
            r.getClient() != null ? r.getClient().getUsername() : null,
            r.getWorker() != null ? r.getWorker().getId() : null,
            r.getWorker() != null ? r.getWorker().getFullName() : null,
            r.getRating(),
            r.getComment(),
            r.getCreatedAt()
        );
    }
}
