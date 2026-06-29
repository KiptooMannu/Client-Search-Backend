package com.kazikonnect.backend.features.dispute.dto;

import lombok.*;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileDisputeRequest {
    private UUID jobId;
    private String disputeReasonKey;
    private String disputeDescription;
    private List<FileEvidenceDTO> evidence;
}
