package com.kazikonnect.backend.features.worker;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {
    List<Document> findAllByWorkerId(UUID workerId);
    void deleteByWorkerId(UUID workerId);
}
