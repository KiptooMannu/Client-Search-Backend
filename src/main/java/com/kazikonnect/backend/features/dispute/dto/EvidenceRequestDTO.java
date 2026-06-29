package com.kazikonnect.backend.features.dispute.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvidenceRequestDTO {
    private UUID id;
    private String requestType;
    private String requestDescription;
    private String requestStatus;
    private String requestedFromUserName;
    private String requestedByAdminName;
    private LocalDateTime dueDate;
    private LocalDateTime createdAt;
    private LocalDateTime fulfilledAt;
}
