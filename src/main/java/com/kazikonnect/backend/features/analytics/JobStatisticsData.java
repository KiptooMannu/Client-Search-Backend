package com.kazikonnect.backend.features.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobStatisticsData {
    private String period;
    private Integer jobsPosted;
    private Integer jobsCompleted;
    private Integer jobsInProgress;
    private Integer jobsPending;
    private Map<String, Integer> jobsByCategory;
}
