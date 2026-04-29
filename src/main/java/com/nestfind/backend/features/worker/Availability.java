package com.nestfind.backend.features.worker;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Availability {
    private boolean weekdays;
    private boolean weekends;
    private boolean evenings;
}
