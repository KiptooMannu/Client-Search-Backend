package com.kazikonnect.backend.features.dispute.dto;

import lombok.*;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignDisputeDTO {
    private UUID disputeId;
    private UUID adminId;
}
