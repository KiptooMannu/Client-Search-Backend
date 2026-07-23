package com.kazikonnect.backend.features.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformFeeData {
    private String period;
    private Double totalFees;
    private Double availableForWithdrawal;
    private Double withdrawn;
    private Double pending;
}
