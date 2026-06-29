package com.kazikonnect.backend.features.dispute.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisputeListItemDTO {
    private UUID id;
    private UUID jobId;
    private String clientName;
    private String workerName;
    private String disputeReasonKey;
    private String priority;
    private String status;
    private Double escrowAmount;
    private LocalDateTime createdAt;
    private String assignedToAdminName;
}
