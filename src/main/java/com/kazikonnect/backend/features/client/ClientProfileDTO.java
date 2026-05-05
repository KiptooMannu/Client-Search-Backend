package com.kazikonnect.backend.features.client;

import java.time.LocalDateTime;
import java.util.UUID;

public record ClientProfileDTO(
    UUID id,
    String username,
    String email,
    String fullName,
    String phoneNumber,
    LocalDateTime createdAt
) {
    public static ClientProfileDTO from(ClientProfile p) {
        return new ClientProfileDTO(
            p.getId(),
            p.getUser() != null ? p.getUser().getUsername() : null,
            p.getUser() != null ? p.getUser().getEmail() : null,
            p.getFullName(),
            p.getPhoneNumber(),
            p.getCreatedAt()
        );
    }
}
