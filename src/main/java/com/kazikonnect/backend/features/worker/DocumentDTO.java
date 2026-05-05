package com.kazikonnect.backend.features.worker;

import java.time.LocalDateTime;
import java.util.UUID;

public record DocumentDTO(
    UUID id,
    UUID workerId,
    String name,
    String type,
    String documentUrl,
    UUID verifiedBy,
    LocalDateTime verifiedAt,
    LocalDateTime uploadedAt
) {
    public static DocumentDTO from(Document d) {
        return new DocumentDTO(
            d.getId(),
            d.getWorker() != null ? d.getWorker().getId() : null,
            d.getName(),
            d.getType(),
            d.getDocumentUrl(),
            d.getVerifiedBy(),
            d.getVerifiedAt(),
            d.getUploadedAt()
        );
    }
}
