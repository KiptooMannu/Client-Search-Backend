package com.nestfind.backend.features.worker;

import com.nestfind.backend.features.auth.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "worker_profiles", indexes = {
    @Index(name = "idx_worker_status", columnList = "status"),
    @Index(name = "idx_worker_is_visible", columnList = "is_visible")
})
@com.fasterxml.jackson.annotation.JsonIdentityInfo(
    generator = com.fasterxml.jackson.annotation.ObjectIdGenerators.PropertyGenerator.class,
    property = "id")
public class WorkerProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @com.fasterxml.jackson.annotation.JsonIgnore
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "experience_years")
    private Integer experienceYears;

    @Column(name = "hourly_rate")
    private Double hourlyRate;

    @Column(name = "profile_picture_url")
    private String profilePictureUrl;

    private String category;

    private String location;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    private WorkerStatus status = WorkerStatus.DRAFT;

    @ElementCollection
    @CollectionTable(name = "worker_locations", joinColumns = @JoinColumn(name = "worker_id"))
    @Column(name = "location_name")
    private Set<String> preferredLocations;

    @org.hibernate.annotations.BatchSize(size = 20)
    @Builder.Default
    @OneToMany(mappedBy = "worker", cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.Set<WorkHistory> workHistory = new java.util.HashSet<>();

    @org.hibernate.annotations.BatchSize(size = 20)
    @Builder.Default
    @OneToMany(mappedBy = "worker", cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.Set<Certification> certifications = new java.util.HashSet<>();

    @org.hibernate.annotations.BatchSize(size = 20)
    @Builder.Default
    @OneToMany(mappedBy = "worker", cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.Set<Document> documents = new java.util.HashSet<>();

    @OneToMany(mappedBy = "worker", cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.Set<Review> reviews;

    @OneToMany(mappedBy = "worker", cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.Set<JobRequest> jobRequests;

    @Builder.Default
    @Column(name = "is_visible")
    private boolean isVisible = false;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Builder.Default
    @Column(name = "is_online")
    private boolean isOnline = false;

    @Column(name = "last_seen")
    private LocalDateTime lastSeen;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "rejected_by")
    private UUID rejectedBy;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    @Embedded
    private Availability availabilityDetails;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @org.hibernate.annotations.BatchSize(size = 20)
    @Builder.Default
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "worker_skills",
        joinColumns = @JoinColumn(name = "worker_id"),
        inverseJoinColumns = @JoinColumn(name = "skill_id")
    )
    private Set<Skill> skills = new java.util.HashSet<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @Override
    public String toString() {
        return "WorkerProfile(id=" + id + ", fullName=" + fullName + ", status=" + status + ", category=" + category + ")";
    }
}
