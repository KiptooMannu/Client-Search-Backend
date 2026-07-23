package com.kazikonnect.backend.features.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevenueData {
    private String period; // e.g., "2024-01", "Week 1", etc.
    private Double revenue;
    private Double platformFees;
    private Double workerPayouts;
}
