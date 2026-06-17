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
    Double clientPartialAmount,
    String paymentStatus,
    Double paymentAmount,
    Double platformFee,
    Double workerNetAmount,
    String escrowMessage,
    String mpesaReceiptNumber,
    Boolean escrowFunded
) {
    public static JobRequestDTO from(JobRequest j) {
        return from(j, null);
    }

    public static JobRequestDTO from(JobRequest j, com.kazikonnect.backend.features.payment.EscrowPayment payment) {
        return new JobRequestDTO(
            j.getId(),
            j.getClient() != null ? j.getClient().getId() : null,
            j.getClient() != null ? j.getClient().getFullName() : null,
            j.getWorker() != null ? j.getWorker().getId() : null,
            j.getWorker() != null ? safeWorkerName(j.getWorker()) : null,
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
            j.getClientPartialAmount(),
            payment != null ? payment.getStatus().name() : null,
            payment != null ? payment.getAmount() : null,
            payment != null ? payment.getPlatformFee() : null,
            payment != null ? payment.getWorkerAmount() : null,
            payment != null ? payment.getMessage() : null,
            payment != null ? payment.getMpesaReceiptNumber() : null,
            j.getEscrowFunded()
        );
    }

    private static String safeWorkerName(WorkerProfile worker) {
        if (worker.getFullName() != null && !worker.getFullName().isBlank()) {
            return worker.getFullName();
        }
        if (worker.getUser() != null) {
            return worker.getUser().getFullName();
        }
        return "Worker";
    }
}

