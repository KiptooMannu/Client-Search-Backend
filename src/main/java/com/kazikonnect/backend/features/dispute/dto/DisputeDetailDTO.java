package com.kazikonnect.backend.features.dispute.dto;

import com.kazikonnect.backend.features.dispute.DisputePriority;
import com.kazikonnect.backend.features.dispute.DisputeStatus;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisputeDetailDTO {
    private UUID id;
    private UUID jobId;
    private UUID escrowPaymentId;
    private String filedByUsername;
    private String filedByEmail;
    private String filedByRole;
    private LocalDateTime filedAt;
    
    private String disputeReasonKey;
    private String disputeDescription;
    private DisputePriority priority;
    private DisputeStatus status;
    
    // Booking Details
    private BookingDetailDTO bookingDetail;
    
    // Payment Information
    private EscrowPaymentDetailDTO escrowPaymentDetail;
    
    // Evidence
    private List<EvidenceDetailDTO> evidence;
    private List<EvidenceRequestDTO> evidenceRequests;
    
    // Messages
    private List<MessageDetailDTO> messages;
    
    // Audit Trail
    private List<AuditTrailDTO> auditTrail;
    
    // Resolution (if resolved)
    private String resolutionType;
    private Double clientResolutionAmount;
    private Double workerResolutionAmount;
    private String adminResolutionReason;
    private LocalDateTime resolvedAt;
    private String resolvedByAdminName;
    
    // Worker & Client Profiles
    private UserProfileDTO clientProfile;
    private UserProfileDTO workerProfile;
}
