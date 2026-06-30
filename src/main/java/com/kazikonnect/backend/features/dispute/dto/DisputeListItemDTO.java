package com.kazikonnect.backend.features.dispute.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisputeListItemDTO {
    private UUID id;
    private UUID jobId;
    private UUID clientId;
    private UUID workerId;
    private String clientName;
    private String workerName;
    private String filedByName;
    private String disputeReasonKey;
    private String disputeDescription;
    private String priority;
    private String status;
    private Double escrowAmount;
    private LocalDateTime createdAt;
    private String assignedToAdminName;
    private List<EvidenceDTO> evidence;
    private String clientEvidence;
    private String workerEvidence;
    private String clientEvidenceAttachmentUrl;
    private String workerEvidenceAttachmentUrl;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvidenceDTO {
        private UUID id;
        private String fileName;
        private String fileUrl;
        private String fileType;
        private String description;
        private String uploadedBy;
        private LocalDateTime createdAt;
    }
}
