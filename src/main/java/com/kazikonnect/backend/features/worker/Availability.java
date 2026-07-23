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
    @Column(name = "availability_weekdays")
    private boolean weekdays;

    @Column(name = "availability_weekends")
    private boolean weekends;

    @Column(name = "availability_evenings")
    private boolean evenings;
}
