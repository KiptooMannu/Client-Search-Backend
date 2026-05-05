package com.kazikonnect.backend.features.worker;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Getter
@Setter
@EqualsAndHashCode(exclude = "worker")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "worker_certifications")
public class Certification {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_id", nullable = false)
    private WorkerProfile worker;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String issuer;

    private Integer year;
}
