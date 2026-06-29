package com.kazikonnect.backend.features.dispute;

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
@Table(name = "dispute_evidence_requests", indexes = {
    @Index(name = "idx_evidence_request_dispute", columnList = "dispute_id"),
    @Index(name = "idx_evidence_request_status", columnList = "request_status"),
    @Index(name = "idx_evidence_request_requested_from", columnList = "requested_from_user_id")
})
public class DisputeEvidenceRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dispute_id", nullable = false)
    private Dispute dispute;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_admin_id", nullable = false)
    private User requestedByAdmin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_from_user_id", nullable = false)
    private User requestedFromUser;

    // Request Details
    @Column(name = "request_type", nullable = false, length = 100)
    private String requestType;  // 'screenshot', 'video', 'receipt', 'work_progress', 'other'

    @Column(name = "request_description", columnDefinition = "TEXT")
    private String requestDescription;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_status", nullable = false, length = 50)
    private EvidenceRequestStatus requestStatus;

    // Due Date
    @Column(name = "due_date")
    private LocalDateTime dueDate;

    // Timestamps
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "fulfilled_at")
    private LocalDateTime fulfilledAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (requestStatus == null) {
            requestStatus = EvidenceRequestStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
