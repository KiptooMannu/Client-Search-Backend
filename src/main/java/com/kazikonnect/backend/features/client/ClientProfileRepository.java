package com.kazikonnect.backend.features.client;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClientProfileRepository extends JpaRepository<ClientProfile, UUID> {
    Optional<ClientProfile> findByUserId(UUID userId);

    Optional<ClientProfile> findByUserEmail(String email);

    List<ClientProfile> findByFullNameContainingIgnoreCaseOrUserEmailContainingIgnoreCase(String fullName,
            String email);
}
