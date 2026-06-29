package com.kazikonnect.backend.features.dispute.dto;

import lombok.*;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolvDisputeRequest {
    private UUID disputeId;
    private String resolutionType;  // 'FULL_REFUND_TO_CLIENT', 'FULL_PAYMENT_TO_WORKER', 'SPLIT'
    private Double clientResolutionAmount;
    private Double workerResolutionAmount;
    private String adminResolutionReason;
    private String adminInternalNotes;
}
