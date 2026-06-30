package com.kazikonnect.backend.features.worker;

import com.kazikonnect.backend.features.auth.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import java.sql.Types;
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

    @JdbcTypeCode(Types.NUMERIC)
    @Column(name = "total_cost", precision = 14, scale = 2)
    private Double totalCost;

    @JdbcTypeCode(Types.NUMERIC)
    @Column(name = "job_price", precision = 14, scale = 2)
    private Double jobPrice;

    @JdbcTypeCode(Types.NUMERIC)
    @Column(name = "negotiated_price", precision = 14, scale = 2)
    private Double negotiatedPrice;

    @Column(name = "required_experience")
    private Integer requiredExperience;

    @OneToOne(mappedBy = "jobRequest", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Review review;

    @Builder.Default
    @Column(name = "review_required")
    private Boolean reviewRequired = false;

    @Builder.Default
    @Column(name = "escrow_funded")
    private Boolean escrowFunded = false;

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

    @JdbcTypeCode(Types.NUMERIC)
    @Column(name = "worker_partial_amount")
    private Double workerPartialAmount;

    @JdbcTypeCode(Types.NUMERIC)
    @Column(name = "client_partial_amount")
    private Double clientPartialAmount;

    @Builder.Default
    @Column(name = "has_active_dispute")
    private Boolean hasActiveDispute = false;

    @Column(name = "title")
    private String title;

    @Column(name = "price_quote")
    @JdbcTypeCode(Types.NUMERIC)
    private Double priceQuote;

    // Cancellation and expiry fields
    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    private String cancellationReason;

    @Column(name = "cancelled_by")
    private UUID cancelledBy;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "expiry_date")
    private LocalDateTime expiryDate;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (hasActiveDispute == null) {
            hasActiveDispute = false;
        }
    }
}