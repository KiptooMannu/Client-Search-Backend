package com.kazikonnect.backend.features.dispute.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageDetailDTO {
    private UUID id;
    private String senderName;
    private String senderRole;
    private String messageType;
    private String messageText;
    private Boolean isAdminOnly;
    private LocalDateTime sentAt;
    private Boolean isReadByClient;
    private Boolean isReadByWorker;
    private Boolean isReadByAdmin;
}
