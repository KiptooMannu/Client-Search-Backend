package com.kazikonnect.backend.features.worker;

public record CertificationCreateDTO(
    String name,
    String issuer,
    Integer year
) {}
