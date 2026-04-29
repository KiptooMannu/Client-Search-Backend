package com.nestfind.backend.features.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface AuthRepository extends JpaRepository<Auth, UUID> {
    Optional<Auth> findByUserEmail(String email);
    Optional<Auth> findByUserId(UUID userId);
}
