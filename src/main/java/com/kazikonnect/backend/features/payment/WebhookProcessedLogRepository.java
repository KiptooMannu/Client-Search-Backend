package com.kazikonnect.backend.features.payment;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookProcessedLogRepository extends JpaRepository<WebhookProcessedLog, String> {
}
