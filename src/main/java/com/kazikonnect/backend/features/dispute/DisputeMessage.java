package com.kazikonnect.backend.features.dispute;

import com.kazikonnect.backend.features.auth.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "dispute_messages", indexes = {
    @Index(name = "idx_message_dispute", columnList = "dispute_id"),
    @Index(name = "idx_message_sender", columnList = "sender_id"),
    @Index(name = "idx_message_created_at", columnList = "created_at")
})
public class DisputeMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dispute_id", nullable = false)
    private Dispute dispute;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", length = 50)
    private MessageType messageType;

    // Message Content
    @Column(name = "message_text", columnDefinition = "TEXT", nullable = false)
    private String messageText;

    @Column(name = "is_admin_only")
    private Boolean isAdminOnly;  // Admin internal notes only

    // Read Status
    @Column(name = "is_read_by_client")
    private Boolean isReadByClient;

    @Column(name = "is_read_by_worker")
    private Boolean isReadByWorker;

    @Column(name = "is_read_by_admin")
    private Boolean isReadByAdmin;

    // Timestamps
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (messageType == null) {
            messageType = MessageType.REGULAR;
        }
        if (isAdminOnly == null) {
            isAdminOnly = false;
        }
        isReadByClient = false;
        isReadByWorker = false;
        isReadByAdmin = false;
    }
}
