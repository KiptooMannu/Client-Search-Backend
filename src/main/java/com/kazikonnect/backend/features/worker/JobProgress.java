package com.kazikonnect.backend.features.worker;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "job_progresses", indexes = {
    @Index(name = "idx_job_progress_job_request", columnList = "job_request_id"),
    @Index(name = "idx_job_progress_created_at", columnList = "created_at")
})
public class JobProgress {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_request_id", nullable = false)
    private JobRequest jobRequest;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "attachment_url")
    private String attachmentUrl;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
