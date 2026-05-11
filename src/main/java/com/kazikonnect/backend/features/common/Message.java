package com.kazikonnect.backend.features.common;

import com.kazikonnect.backend.features.auth.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "messages", indexes = {
    @Index(name = "idx_sender_receiver", columnList = "sender_id,receiver_id"),
    @Index(name = "idx_sent_at", columnList = "sent_at"),
    @Index(name = "idx_is_read", columnList = "is_read"),
    @Index(name = "idx_receiver_id_read", columnList = "receiver_id,is_read")
})
public class Message {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id", nullable = false)
    private User receiver;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "attachment_url")
    private String attachmentUrl;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Builder.Default
    @Column(name = "is_read")
    private boolean isRead = false;

    @PrePersist
    protected void onCreate() {
        sentAt = LocalDateTime.now();
    }
}
