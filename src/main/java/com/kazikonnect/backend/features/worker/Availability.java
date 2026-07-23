package com.kazikonnect.backend.features.worker;

import jakarta.persistence.Column;
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
    @Column(name = "weekdays")
    private boolean weekdays;

    @Column(name = "weekends")
    private boolean weekends;

    @Column(name = "evenings")
    private boolean evenings;
}
