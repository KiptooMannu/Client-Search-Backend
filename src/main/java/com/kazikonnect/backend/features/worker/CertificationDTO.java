package com.kazikonnect.backend.features.worker;

import java.util.UUID;

public record CertificationDTO(
    UUID id,
    String name,
    String issuer,
    Integer year
) {
    public static CertificationDTO from(Certification c) {
        return new CertificationDTO(c.getId(), c.getName(), c.getIssuer(), c.getYear());
    }
}
