package com.kazikonnect.backend.features.worker;

import com.kazikonnect.backend.features.auth.User;
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
@Table(name = "job_requests", indexes = {
    @Index(name = "idx_job_client", columnList = "client_id"),
    @Index(name = "idx_job_worker", columnList = "worker_id"),
    @Index(name = "idx_job_status", columnList = "status")
})
public class JobRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private User client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_id", nullable = false)
    private WorkerProfile worker;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    private JobStatus status = JobStatus.PENDING;

    @Column(name = "handled_by")
    private UUID handledBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "total_cost")
    private Double totalCost;



    @OneToOne(mappedBy = "jobRequest", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Review review;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}

