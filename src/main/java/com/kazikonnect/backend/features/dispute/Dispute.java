package com.kazikonnect.backend.features.dispute;

import com.kazikonnect.backend.features.auth.User;
import com.kazikonnect.backend.features.payment.EscrowPayment;
import com.kazikonnect.backend.features.worker.JobRequest;
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
@Table(name = "disputes", indexes = {
    @Index(name = "idx_dispute_job", columnList = "job_request_id"),
    @Index(name = "idx_dispute_escrow", columnList = "escrow_payment_id"),
    @Index(name = "idx_dispute_filed_by", columnList = "filed_by_id"),
    @Index(name = "idx_dispute_status", columnList = "dispute_status"),
    @Index(name = "idx_dispute_created_at", columnList = "created_at"),
    @Index(name = "idx_dispute_priority", columnList = "dispute_priority")
})
public class Dispute {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    private Long version;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_request_id", nullable = false)
    private JobRequest jobRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "escrow_payment_id", nullable = false)
    private EscrowPayment escrowPayment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "filed_by_id", nullable = false)
    private User filedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to_admin_id")
    private User assignedToAdmin;

    // Dispute Information
    @Column(name = "dispute_reason_key", nullable = false, length = 100)
    private String disputeReasonKey;

    @Column(name = "dispute_description", columnDefinition = "TEXT", nullable = false)
    private String disputeDescription;

    @Enumerated(EnumType.STRING)
    @Column(name = "dispute_priority", nullable = false, length = 20)
    private DisputePriority priority;

    @Enumerated(EnumType.STRING)
    @Column(name = "dispute_status", nullable = false, length = 50)
    private DisputeStatus status;

    // Resolution Information (NULL until resolved)
    @Enumerated(EnumType.STRING)
    @Column(name = "resolution_type", length = 50)
    private ResolutionType resolutionType;

    @Column(name = "client_resolution_amount")
    private Double clientResolutionAmount;

    @Column(name = "worker_resolution_amount")
    private Double workerResolutionAmount;

    @Column(name = "admin_resolution_reason", columnDefinition = "TEXT")
    private String adminResolutionReason;

    @Column(name = "admin_internal_notes", columnDefinition = "TEXT")
    private String adminInternalNotes;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by_admin_id")
    private User resolvedByAdmin;

    // Timestamps
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "evidence_requested_at")
    private LocalDateTime evidenceRequestedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) {
            status = DisputeStatus.OPEN;
        }
        if (priority == null) {
            priority = DisputePriority.MEDIUM;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
