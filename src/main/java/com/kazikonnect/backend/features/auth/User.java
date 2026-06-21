package com.kazikonnect.backend.features.auth;

import com.kazikonnect.backend.features.common.Message;
import com.kazikonnect.backend.features.common.Notification;
import com.kazikonnect.backend.features.worker.JobRequest;
import com.kazikonnect.backend.features.worker.Review;
import com.kazikonnect.backend.features.client.ClientProfile;
import com.kazikonnect.backend.features.worker.WorkerProfile;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_user_role", columnList = "role")
})
@com.fasterxml.jackson.annotation.JsonIdentityInfo(
    generator = com.fasterxml.jackson.annotation.ObjectIdGenerators.PropertyGenerator.class,
    property = "id")
public class User implements Persistable<UUID> {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String secondName;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "profile_picture_url")
    private String profilePictureUrl;

    @Enumerated(EnumType.STRING)
    private UserRole role;

    @JsonIgnore
    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Auth auth;

    @JsonIgnore
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.List<RefreshToken> refreshTokens;

    @JsonIgnore
    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private WorkerProfile workerProfile;

    @JsonIgnore
    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private ClientProfile clientProfile;

    @JsonIgnore
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.List<Notification> notifications;

    @JsonIgnore
    @OneToMany(mappedBy = "client", cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.List<Review> clientReviews;

    @JsonIgnore
    @OneToMany(mappedBy = "client", cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.List<JobRequest> jobRequests;

    @JsonIgnore
    @OneToMany(mappedBy = "sender", cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.List<Message> sentMessages;

    @JsonIgnore
    @OneToMany(mappedBy = "receiver", cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.List<Message> receivedMessages;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /**
     * Transient flag used to tell Hibernate/Spring Data definitively whether
     * this entity is new (should be INSERTed) or already persisted (should be
     * UPDATEd/merged). Without this, Hibernate falls back to "id == null means
     * new", which breaks whenever we manually assign a fixed UUID to a
     * brand-new entity (e.g. deterministic seed data) -- it would incorrectly
     * attempt a merge/update against a row that doesn't exist yet and throw
     * an ObjectOptimisticLockingFailureException / StaleObjectStateException.
     */
    @Builder.Default
    @Transient
    private boolean isNew = true;

    @Override
    public boolean isNew() {
        return isNew || id == null;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updateFullName();
        this.isNew = false;
    }

    @PreUpdate
    protected void onUpdate() {
        updateFullName();
    }

    @PostLoad
    protected void onLoad() {
        this.isNew = false;
    }

    private void updateFullName() {
        if (firstName != null && secondName != null && !firstName.isBlank() && !secondName.isBlank()) {
            this.fullName = firstName + " " + secondName;
        } else if (firstName != null && !firstName.isBlank()) {
            this.fullName = firstName;
        } else if (secondName != null && !secondName.isBlank()) {
            this.fullName = secondName;
        } else if (this.fullName == null || this.fullName.equals("Unknown")) {
            this.fullName = username;
        }
    }

    public String getFullName() {
        if (fullName != null && !fullName.equals("Unknown") && !fullName.isBlank()) {
            return fullName;
        }
        if (firstName != null && secondName != null && !firstName.isBlank() && !secondName.isBlank()) {
            return firstName + " " + secondName;
        }
        return username != null ? username : "Unknown User";
    }

    @Override
    public String toString() {
        return "User(id=" + id + ", username=" + username + ", email=" + email + ", role=" + role + ")";
    }
}