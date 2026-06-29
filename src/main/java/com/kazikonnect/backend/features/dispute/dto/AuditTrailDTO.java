package com.kazikonnect.backend.features.dispute.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditTrailDTO {
    private UUID id;
    private String actionType;
    private String actionDescription;
    private String actorName;
    private String actorRole;
    private String oldValue;
    private String newValue;
    private Object additionalData;
    private LocalDateTime timestamp;
}
