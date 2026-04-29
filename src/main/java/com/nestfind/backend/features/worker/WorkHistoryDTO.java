package com.nestfind.backend.features.worker;

import java.util.UUID;

public record WorkHistoryDTO(
    UUID id,
    String role,
    String company,
    String period,
    String description
) {
    public static WorkHistoryDTO from(WorkHistory w) {
        return new WorkHistoryDTO(w.getId(), w.getRole(), w.getCompany(), w.getPeriod(), w.getDescription());
    }
}
