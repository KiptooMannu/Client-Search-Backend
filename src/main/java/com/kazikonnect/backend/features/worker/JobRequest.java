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

    
    @Column(name = "required_experience")
    private Integer requiredExperience;

    @OneToOne(mappedBy = "jobRequest", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Review review;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "disputed_at")
    private LocalDateTime disputedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "deadline")
    private LocalDateTime deadline;

    @Column(name = "dispute_reason", columnDefinition = "TEXT")
    private String disputeReason;

    @Column(name = "dispute_evidence", columnDefinition = "TEXT")
    private String disputeEvidence;

    @Column(name = "dispute_attachment_url")
    private String disputeAttachmentUrl;

    @Column(name = "dispute_response", columnDefinition = "TEXT")
    private String disputeResponse;

    @Column(name = "dispute_response_evidence", columnDefinition = "TEXT")
    private String disputeResponseEvidence;

    @Column(name = "dispute_response_attachment_url")
    private String disputeResponseAttachmentUrl;

    @Column(name = "admin_decision_reason", columnDefinition = "TEXT")
    private String adminDecisionReason;

    @Column(name = "admin_evidence_notes", columnDefinition = "TEXT")
    private String adminEvidenceNotes;

    @Column(name = "worker_partial_amount")
    private Double workerPartialAmount;

    @Column(name = "client_partial_amount")
    private Double clientPartialAmount;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}

