package com.kazikonnect.backend.features.dispute.dto;

import lombok.*;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestEvidenceDTO {
    private UUID disputeId;
    private UUID requestFromUserId;  // The user from whom evidence is requested
    private String requestType;  // 'screenshot', 'video', 'receipt', 'work_progress', 'other'
    private String requestDescription;
    private String dueDateDays;  // Number of days until due (default 5)
}
