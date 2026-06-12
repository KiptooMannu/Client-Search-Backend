package com.kazikonnect.backend.features.worker;

import java.time.LocalDateTime;
import java.util.UUID;

public record JobRequestDTO(
    UUID id,
    UUID clientId,
    String clientName,
    UUID workerId,
    String workerName,
    String description,
    String status,
    LocalDateTime createdAt,
    Double totalCost,
    Integer rating,
    Integer requiredExperience,
    LocalDateTime startedAt,
    LocalDateTime submittedAt,
    LocalDateTime approvedAt,
    LocalDateTime disputedAt,
    LocalDateTime resolvedAt,
    LocalDateTime deadline,
    String disputeReason,
    String disputeEvidence,
    String disputeAttachmentUrl,
    String disputeResponse,
    String disputeResponseEvidence,
    String disputeResponseAttachmentUrl,
    String adminDecisionReason,
    String adminEvidenceNotes,
    Double workerPartialAmount,
    Double clientPartialAmount
) {
    public static JobRequestDTO from(JobRequest j) {
        return new JobRequestDTO(
            j.getId(),
            j.getClient() != null ? j.getClient().getId() : null,
            j.getClient() != null ? j.getClient().getUsername() : null,
            j.getWorker() != null ? j.getWorker().getId() : null,
            j.getWorker() != null ? j.getWorker().getFullName() : null,
            j.getDescription(),
            j.getStatus().name(),
            j.getCreatedAt(),
            j.getTotalCost(),
            j.getReview() != null ? j.getReview().getRating() : null,
            j.getRequiredExperience(),
            j.getStartedAt(),
            j.getSubmittedAt(),
            j.getApprovedAt(),
            j.getDisputedAt(),
            j.getResolvedAt(),
            j.getDeadline(),
            j.getDisputeReason(),
            j.getDisputeEvidence(),
            j.getDisputeAttachmentUrl(),
            j.getDisputeResponse(),
            j.getDisputeResponseEvidence(),
            j.getDisputeResponseAttachmentUrl(),
            j.getAdminDecisionReason(),
            j.getAdminEvidenceNotes(),
            j.getWorkerPartialAmount(),
            j.getClientPartialAmount()
        );
    }
}

