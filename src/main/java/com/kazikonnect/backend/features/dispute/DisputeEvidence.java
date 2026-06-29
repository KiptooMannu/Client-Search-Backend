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
@Table(name = "dispute_evidence", indexes = {
    @Index(name = "idx_evidence_dispute", columnList = "dispute_id"),
    @Index(name = "idx_evidence_uploaded_by", columnList = "uploaded_by_id"),
    @Index(name = "idx_evidence_created_at", columnList = "created_at")
})
public class DisputeEvidence {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dispute_id", nullable = false)
    private Dispute dispute;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by_id", nullable = false)
    private User uploadedBy;

    // File Information
    @Column(name = "file_name", nullable = false, length = 500)
    private String fileName;

    @Column(name = "file_url", nullable = false, length = 1000)
    private String fileUrl;

    @Column(name = "file_type", length = 50)
    private String fileType;  // 'screenshot', 'photo', 'video', 'pdf', 'contract', 'receipt', 'other'

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    // Metadata
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_admin_requested")
    private Boolean isAdminRequested;

    @Column(name = "admin_evidence_request_type", length = 100)
    private String adminEvidenceRequestType;  // Type of evidence admin requested

    // Timestamps
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (isAdminRequested == null) {
            isAdminRequested = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
