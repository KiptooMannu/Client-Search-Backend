package com.nestfind.backend.features.worker;

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
@Table(name = "worker_work_history")
public class WorkHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "worker_id", nullable = false)
    private WorkerProfile worker;

    @Column(nullable = false)
    private String role;

    @Column(nullable = false)
    private String company;

    private String period;

    @Column(columnDefinition = "TEXT")
    private String description;
}
