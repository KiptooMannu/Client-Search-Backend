package com.kazikonnect.backend.features.common;

import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationDTO(
    UUID id,
    UUID userId,
    String title,
    String message,
    String type,
    boolean isRead,
    LocalDateTime createdAt
) {
    public static NotificationDTO from(Notification n) {
        return new NotificationDTO(
            n.getId(),
            n.getUser() != null ? n.getUser().getId() : null,
            n.getTitle(),
            n.getMessage(),
            n.getType(),
            n.isRead(),
            n.getCreatedAt()
        );
    }
}
