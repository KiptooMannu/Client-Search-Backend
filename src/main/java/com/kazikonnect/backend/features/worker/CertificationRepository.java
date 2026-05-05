package com.kazikonnect.backend.features.worker;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface CertificationRepository extends JpaRepository<Certification, UUID> {
    java.util.List<Certification> findAllByWorkerId(UUID workerId);
    void deleteByWorkerId(UUID workerId);
}
