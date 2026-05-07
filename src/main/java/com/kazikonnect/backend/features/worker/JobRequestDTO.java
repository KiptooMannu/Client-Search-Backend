package com.kazikonnect.backend.features.worker;

import java.time.LocalDateTime;
import java.util.UUID;

public record JobRequestDTO(
    UUID id,
    UUID clientId,
    String clientName,
    UUID workerId,
    String workerName,
    String description,
    String status,
    LocalDateTime createdAt,
    Double totalCost
) {
    public static JobRequestDTO from(JobRequest j) {
        return new JobRequestDTO(
            j.getId(),
            j.getClient() != null ? j.getClient().getId() : null,
            j.getClient() != null ? j.getClient().getUsername() : null,
            j.getWorker() != null ? j.getWorker().getId() : null,
            j.getWorker() != null ? j.getWorker().getFullName() : null,
            j.getDescription(),
            j.getStatus().name(),
            j.getCreatedAt(),
            j.getTotalCost()
        );
    }
}
