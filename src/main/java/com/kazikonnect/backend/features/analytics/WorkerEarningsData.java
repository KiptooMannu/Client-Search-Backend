package com.kazikonnect.backend.features.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkerEarningsData {
    private String period;
    private Double earnings;
    private Integer jobsCompleted;
}
