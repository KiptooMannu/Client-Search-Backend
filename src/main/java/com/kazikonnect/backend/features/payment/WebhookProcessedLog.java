package com.kazikonnect.backend.features.payment;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.lang.NonNull;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "mpesa_webhook_logs")
public class WebhookProcessedLog {

    @Id
    @Column(name = "id", nullable = false, unique = true)
    @NonNull
    private String id;

    @Column(name = "checkout_request_id")
    private String checkoutRequestId;

    @Column(name = "result_code")
    private Integer resultCode;

    @Column(columnDefinition = "TEXT")
    private String resultDescription;

    @Column(columnDefinition = "TEXT")
    private String payload;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @PrePersist
    protected void onPersist() {
        processedAt = LocalDateTime.now();
    }
}