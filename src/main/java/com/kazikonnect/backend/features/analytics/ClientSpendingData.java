package com.kazikonnect.backend.features.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientSpendingData {
    private String period;
    private Double amount;
    private String category;
}
