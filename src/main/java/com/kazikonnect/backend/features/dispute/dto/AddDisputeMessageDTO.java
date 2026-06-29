package com.kazikonnect.backend.features.dispute.dto;

import lombok.*;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddDisputeMessageDTO {
    private UUID disputeId;
    private String messageText;
    private String messageType;  // 'REGULAR', 'EVIDENCE_REQUEST', 'ADMIN_NOTE', 'RESOLUTION', 'NOTIFICATION'
    private Boolean isAdminOnly;
}
