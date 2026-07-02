package com.kazikonnect.backend.features.worker;

import java.time.LocalDateTime;
import java.util.UUID;

public record JobProgressDTO(
    UUID id,
    UUID jobId,
    String description,
    String attachmentUrl,
    LocalDateTime createdAt
) {
    public static JobProgressDTO from(JobProgress progress) {
        return new JobProgressDTO(
            progress.getId(),
            progress.getJobRequest() != null ? progress.getJobRequest().getId() : null,
            progress.getDescription(),
            progress.getAttachmentUrl(),
            progress.getCreatedAt()
        );
    }
}
