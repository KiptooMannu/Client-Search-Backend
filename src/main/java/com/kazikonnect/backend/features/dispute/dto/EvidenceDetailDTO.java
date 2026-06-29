package com.kazikonnect.backend.features.dispute.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvidenceDetailDTO {
    private UUID id;
    private String fileName;
    private String fileUrl;
    private String fileType;
    private String description;
    private String uploadedByName;
    private String uploadedByRole;
    private Boolean isAdminRequested;
    private String adminEvidenceRequestType;
    private LocalDateTime uploadedAt;
}
