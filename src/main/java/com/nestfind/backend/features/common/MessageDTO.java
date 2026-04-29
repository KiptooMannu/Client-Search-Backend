package com.nestfind.backend.features.common;

import java.time.LocalDateTime;
import java.util.UUID;

public record MessageDTO(
    UUID id,
    UUID senderId,
    String senderName,
    UUID receiverId,
    String receiverName,
    String content,
    String attachmentUrl,
    LocalDateTime sentAt,
    boolean isRead
) {
    public static MessageDTO from(Message m) {
        return new MessageDTO(
            m.getId(),
            m.getSender() != null ? m.getSender().getId() : null,
            m.getSender() != null ? m.getSender().getUsername() : null,
            m.getReceiver() != null ? m.getReceiver().getId() : null,
            m.getReceiver() != null ? m.getReceiver().getUsername() : null,
            m.getContent(),
            m.getAttachmentUrl(),
            m.getSentAt(),
            m.isRead()
        );
    }
}
