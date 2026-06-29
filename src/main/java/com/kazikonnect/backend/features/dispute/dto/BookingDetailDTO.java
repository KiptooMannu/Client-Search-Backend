package com.kazikonnect.backend.features.dispute.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingDetailDTO {
    private UUID jobId;
    private String title;
    private String description;
    private Double budget;
    private LocalDateTime deadline;
    private LocalDateTime startedAt;
    private LocalDateTime submittedAt;
    private LocalDateTime approvedAt;
    private String status;
    private LocalDateTime createdAt;
}
