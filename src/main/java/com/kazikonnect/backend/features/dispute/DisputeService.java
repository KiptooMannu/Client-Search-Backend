package com.kazikonnect.backend.features.dispute;

import com.kazikonnect.backend.features.auth.User;
import com.kazikonnect.backend.features.auth.UserRepository;
import com.kazikonnect.backend.features.auth.UserRole;
import com.kazikonnect.backend.features.dispute.dto.*;
import com.kazikonnect.backend.features.payment.EscrowPayment;
import com.kazikonnect.backend.features.payment.EscrowPaymentRepository;
import com.kazikonnect.backend.features.payment.EscrowPaymentStatus;
import com.kazikonnect.backend.features.wallet.WalletService;
import com.kazikonnect.backend.features.worker.JobRequest;
import com.kazikonnect.backend.features.worker.JobRequestRepository;
import com.kazikonnect.backend.features.worker.JobStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
@SuppressWarnings({"null"}) 
public class DisputeService {

    private final DisputeRepository disputeRepository;
    private final DisputeEvidenceRepository evidenceRepository;
    private final DisputeMessageRepository messageRepository;
    private final DisputeAuditTrailRepository auditTrailRepository;
    private final DisputeEvidenceRequestRepository evidenceRequestRepository;
    private final JobRequestRepository jobRequestRepository;
    private final EscrowPaymentRepository escrowPaymentRepository;
    private final UserRepository userRepository;
    private final WalletService walletService;
    private final ObjectMapper objectMapper;

    // ─────────────────────────────────────────────────────────────────────────
    // FILE DISPUTE
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * File a dispute for a booking with escrow payment
     */
    public Dispute fileDispute(FileDisputeRequest request, Principal principal) {
        log.info("Filing dispute for job: {}", request.getJobId());

        User actor = getActorFromPrincipal(principal);
        
        // Get job request
        JobRequest jobRequest = jobRequestRepository.findById(request.getJobId())
                .orElseThrow(() -> new RuntimeException("Job not found"));

        // Verify actor is either client or worker
        boolean isClient = jobRequest.getClient().getId().equals(actor.getId());
        boolean isWorker = jobRequest.getWorker() != null && 
                          jobRequest.getWorker().getUser().getId().equals(actor.getId());
        
        if (!isClient && !isWorker) {
            throw new RuntimeException("Unauthorized: you are not part of this booking");
        }

        // Check if dispute already exists
        if (disputeRepository.findByJobRequestId(request.getJobId()).isPresent()) {
            throw new RuntimeException("A dispute already exists for this booking");
        }

        // Get escrow payment - allow disputes on funded payments (SUCCESS/ESCROWED/RELEASED/PARTIALLY_SETTLED)
        Optional<EscrowPayment> escrowPaymentOpt = escrowPaymentRepository
                .findTopByJobRequestIdAndStatusInOrderByCreatedAtDesc(
                        request.getJobId(),
                        List.of(EscrowPaymentStatus.SUCCESS, EscrowPaymentStatus.ESCROWED, 
                               EscrowPaymentStatus.RELEASED, EscrowPaymentStatus.PARTIALLY_SETTLED));

        EscrowPayment escrowPayment = escrowPaymentOpt.orElseGet(() -> {
            // Fallback: look for any escrow payment regardless of status
            Optional<EscrowPayment> latestPayment = escrowPaymentRepository
                    .findTopByJobRequestIdOrderByCreatedAtDesc(request.getJobId());

            if (latestPayment.isEmpty()) {
                throw new RuntimeException("No escrow payment found for this booking");
            }

            EscrowPayment payment = latestPayment.get();
            log.warn("Dispute filed for job {} with escrow payment in status: {}. " +
                    "Money may have already been transferred.", request.getJobId(), payment.getStatus());
            return payment;
        });

        // Create dispute
        Dispute dispute = Dispute.builder()
                .jobRequest(jobRequest)
                .escrowPayment(escrowPayment)
                .filedBy(actor)
                .disputeReasonKey(request.getDisputeReasonKey())
                .disputeDescription(request.getDisputeDescription())
                .status(DisputeStatus.OPEN)
                .priority(DisputePriority.MEDIUM)
                .build();

        Dispute savedDispute = disputeRepository.save(dispute);

        // Lock escrow payment
        escrowPayment.setIsLockedByDispute(true);
        escrowPayment.setStatus(EscrowPaymentStatus.DISPUTED);
        escrowPaymentRepository.save(escrowPayment);

        // Mark job as having active dispute and change status to DISPUTED
        jobRequest.setHasActiveDispute(true);
        jobRequest.setDisputedAt(LocalDateTime.now());
        jobRequest.setStatus(JobStatus.DISPUTED);
        jobRequestRepository.save(jobRequest);

        // Upload evidence
        if (request.getEvidence() != null && !request.getEvidence().isEmpty()) {
            for (FileEvidenceDTO evidence : request.getEvidence()) {
                DisputeEvidence evidenceRecord = DisputeEvidence.builder()
                        .dispute(savedDispute)
                        .uploadedBy(actor)
                        .fileName(evidence.getFileName())
                        .fileUrl(evidence.getFileUrl())
                        .fileType(evidence.getFileType())
                        .fileSizeBytes(evidence.getFileSizeBytes())
                        .mimeType(evidence.getMimeType())
                        .description(evidence.getDescription())
                        .isAdminRequested(false)
                        .build();
                evidenceRepository.save(evidenceRecord);
            }
        }

        // Log to audit trail
        logAuditAction(
                savedDispute,
                actor,
                "DISPUTE_FILED",
                "Dispute filed by " + actor.getUsername(),
                null,
                DisputeStatus.OPEN.toString(),
                null
        );

        log.info("Dispute created: {} for job: {}", savedDispute.getId(), request.getJobId());
        return savedDispute;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // EVIDENCE MANAGEMENT
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Add evidence to an existing dispute
     */
    public void addEvidence(UUID disputeId, List<FileEvidenceDTO> evidenceList, Principal principal) {
        log.info("Adding evidence to dispute: {}", disputeId);

        User actor = getActorFromPrincipal(principal);
        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new RuntimeException("Dispute not found"));

        // Verify actor is involved in the dispute
        verifyDisputeAccess(dispute, actor);

        for (FileEvidenceDTO evidence : evidenceList) {
            DisputeEvidence evidenceRecord = DisputeEvidence.builder()
                    .dispute(dispute)
                    .uploadedBy(actor)
                    .fileName(evidence.getFileName())
                    .fileUrl(evidence.getFileUrl())
                    .fileType(evidence.getFileType())
                    .fileSizeBytes(evidence.getFileSizeBytes())
                    .mimeType(evidence.getMimeType())
                    .description(evidence.getDescription())
                    .isAdminRequested(false)
                    .build();
            evidenceRepository.save(evidenceRecord);
        }

        logAuditAction(
                dispute,
                actor,
                "EVIDENCE_UPLOADED",
                "Evidence uploaded by " + actor.getUsername(),
                null,
                String.valueOf(evidenceList.size()),
                null
        );
    }

    /**
     * Admin requests evidence from a party
     */
    public DisputeEvidenceRequest requestEvidence(RequestEvidenceDTO request, Principal principal) {
        log.info("Requesting evidence for dispute: {}", request.getDisputeId());

        User admin = getActorFromPrincipal(principal);
        if (admin.getRole() != UserRole.ADMIN) {
            throw new RuntimeException("Only admins can request evidence");
        }

        Dispute dispute = disputeRepository.findById(request.getDisputeId())
                .orElseThrow(() -> new RuntimeException("Dispute not found"));

        User requestedFromUser = userRepository.findById(request.getRequestFromUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Create evidence request
        DisputeEvidenceRequest evidenceRequest = DisputeEvidenceRequest.builder()
                .dispute(dispute)
                .requestedByAdmin(admin)
                .requestedFromUser(requestedFromUser)
                .requestType(request.getRequestType())
                .requestDescription(request.getRequestDescription())
                .requestStatus(EvidenceRequestStatus.PENDING)
                .dueDate(LocalDateTime.now().plusDays(Integer.parseInt(request.getDueDateDays() != null ? 
                        request.getDueDateDays() : "5")))
                .build();

        DisputeEvidenceRequest savedRequest = evidenceRequestRepository.save(evidenceRequest);

        // Update dispute status
        if (dispute.getStatus() != DisputeStatus.AWAITING_EVIDENCE) {
            dispute.setStatus(DisputeStatus.AWAITING_EVIDENCE);
            dispute.setEvidenceRequestedAt(LocalDateTime.now());
            disputeRepository.save(dispute);
        }

        logAuditAction(
                dispute,
                admin,
                "EVIDENCE_REQUESTED",
                "Evidence requested from " + requestedFromUser.getUsername(),
                null,
                request.getRequestType(),
                null
        );

        return savedRequest;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MESSAGING & COMMUNICATION
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Add a message to dispute discussion
     */
    public DisputeMessage addMessage(AddDisputeMessageDTO request, Principal principal) {
        log.info("Adding message to dispute: {}", request.getDisputeId());

        User sender = getActorFromPrincipal(principal);
        Dispute dispute = disputeRepository.findById(request.getDisputeId())
                .orElseThrow(() -> new RuntimeException("Dispute not found"));

        // Verify sender access
        verifyDisputeAccess(dispute, sender);

        MessageType messageType = MessageType.valueOf(request.getMessageType() != null ? 
                request.getMessageType() : "REGULAR");

        DisputeMessage message = DisputeMessage.builder()
                .dispute(dispute)
                .sender(sender)
                .messageType(messageType)
                .messageText(request.getMessageText())
                .isAdminOnly(request.getIsAdminOnly() != null && request.getIsAdminOnly())
                .build();

        return messageRepository.save(message);
    }

    /**
     * Mark message as read by user
     */
    public void markMessageAsRead(UUID messageId, Principal principal) {
        User user = getActorFromPrincipal(principal);
        DisputeMessage message = messageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));

        if (user.getRole() == UserRole.CLIENT) {
            message.setIsReadByClient(true);
        } else if (user.getRole() == UserRole.WORKER) {
            message.setIsReadByWorker(true);
        } else if (user.getRole() == UserRole.ADMIN) {
            message.setIsReadByAdmin(true);
        }

        messageRepository.save(message);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DISPUTE RESOLUTION
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Resolve dispute by issuing a resolution
     */
    public Dispute resolveDispute(ResolvDisputeRequest request, Principal principal) {
        log.info("Resolving dispute: {}", request.getDisputeId());

        User admin = getActorFromPrincipal(principal);
        if (admin.getRole() != UserRole.ADMIN) {
            throw new RuntimeException("Only admins can resolve disputes");
        }

        Dispute dispute = disputeRepository.findById(request.getDisputeId())
                .orElseThrow(() -> new RuntimeException("Dispute not found"));

        ResolutionType resolutionType = ResolutionType.valueOf(request.getResolutionType());
        EscrowPayment escrowPayment = dispute.getEscrowPayment();
        Double totalAmount = escrowPayment.getAmount();

        // Validate resolution amounts
        Double clientAmount = 0.0;
        Double workerAmount = 0.0;

        switch (resolutionType) {
            case FULL_REFUND_TO_CLIENT:
                clientAmount = totalAmount;
                workerAmount = 0.0;
                break;
            case FULL_PAYMENT_TO_WORKER:
                clientAmount = 0.0;
                workerAmount = totalAmount;
                break;
            case SPLIT:
                if (request.getClientResolutionAmount() == null || 
                    request.getWorkerResolutionAmount() == null) {
                    throw new RuntimeException("Split resolution requires both amounts");
                }
                if (!request.getClientResolutionAmount().equals(
                        totalAmount - request.getWorkerResolutionAmount())) {
                    throw new RuntimeException("Resolution amounts do not equal escrow balance");
                }
                clientAmount = request.getClientResolutionAmount();
                workerAmount = request.getWorkerResolutionAmount();
                break;
        }

        // Update dispute
        dispute.setResolutionType(resolutionType);
        dispute.setClientResolutionAmount(clientAmount);
        dispute.setWorkerResolutionAmount(workerAmount);
        dispute.setAdminResolutionReason(request.getAdminResolutionReason());
        dispute.setAdminInternalNotes(request.getAdminInternalNotes());
        dispute.setStatus(DisputeStatus.RESOLVED);
        dispute.setResolvedAt(LocalDateTime.now());
        dispute.setResolvedByAdmin(admin);

        Dispute savedDispute = disputeRepository.save(dispute);

        // Distribute funds
        distributeFunds(dispute, clientAmount, workerAmount);

        // Unlock escrow
        escrowPayment.setIsLockedByDispute(false);
        escrowPaymentRepository.save(escrowPayment);

        // Update job status
        JobRequest job = dispute.getJobRequest();
        job.setHasActiveDispute(false);
        job.setResolvedAt(LocalDateTime.now());
        jobRequestRepository.save(job);

        // Log resolution
        logAuditAction(
                savedDispute,
                admin,
                "RESOLUTION_ISSUED",
                "Dispute resolved: " + resolutionType.toString(),
                null,
                resolutionType.toString(),
                objectMapper.valueToTree(Map.of(
                        "clientAmount", clientAmount,
                        "workerAmount", workerAmount
                ))
        );

        log.info("Dispute resolved: {}", savedDispute.getId());
        return savedDispute;
    }

    /**
     * Distribute funds according to resolution
     */
    private void distributeFunds(Dispute dispute, Double clientAmount, Double workerAmount) {
        JobRequest job = dispute.getJobRequest();
        EscrowPayment payment = dispute.getEscrowPayment();

        // Refund to client
        if (clientAmount > 0) {
            walletService.creditWallet(
                    job.getClient(),
                    clientAmount,
                    "Dispute resolution refund for job " + job.getId()
            );
            payment.setStatus(EscrowPaymentStatus.REFUNDED);
        }

        // Payment to worker
        if (workerAmount > 0) {
            walletService.creditWallet(
                    job.getWorker().getUser(),
                    workerAmount,
                    "Dispute resolution payment for job " + job.getId()
            );
            payment.setStatus(EscrowPaymentStatus.RELEASED);
        }

        // Update job status
        if (workerAmount > 0 && clientAmount == 0) {
            job.setStatus(JobStatus.APPROVED);
        } else if (clientAmount > 0 && workerAmount == 0) {
            job.setStatus(JobStatus.CANCELLED);
        } else {
            job.setStatus(JobStatus.APPROVED);  // Split case
        }

        escrowPaymentRepository.save(payment);
        jobRequestRepository.save(job);

        logAuditAction(
                dispute,
                dispute.getResolvedByAdmin(),
                "FUNDS_DISTRIBUTED",
                "Funds distributed per resolution",
                null,
                "COMPLETED",
                null
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ADMIN MANAGEMENT
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Assign dispute to admin
     */
    public Dispute assignDisputeToAdmin(AssignDisputeDTO request, Principal principal) {
        log.info("Assigning dispute to admin: {}", request.getDisputeId());

        User currentAdmin = getActorFromPrincipal(principal);
        if (currentAdmin.getRole() != UserRole.ADMIN) {
            throw new RuntimeException("Only admins can assign disputes");
        }

        Dispute dispute = disputeRepository.findById(request.getDisputeId())
                .orElseThrow(() -> new RuntimeException("Dispute not found"));

        User assignedAdmin = userRepository.findById(request.getAdminId())
                .orElseThrow(() -> new RuntimeException("Admin user not found"));

        if (assignedAdmin.getRole() != UserRole.ADMIN) {
            throw new RuntimeException("Assigned user must be an admin");
        }

        dispute.setAssignedToAdmin(assignedAdmin);
        Dispute saved = disputeRepository.save(dispute);

        logAuditAction(
                saved,
                currentAdmin,
                "ASSIGNED_TO_ADMIN",
                "Dispute assigned to " + assignedAdmin.getUsername(),
                null,
                assignedAdmin.getUsername(),
                null
        );

        return saved;
    }

    /**
     * Change dispute priority
     */
    public Dispute changePriority(UUID disputeId, String priority, Principal principal) {
        log.info("Changing priority for dispute: {} to: {}", disputeId, priority);

        User admin = getActorFromPrincipal(principal);
        if (admin.getRole() != UserRole.ADMIN) {
            throw new RuntimeException("Only admins can change priority");
        }

        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new RuntimeException("Dispute not found"));

        DisputePriority oldPriority = dispute.getPriority();
        DisputePriority newPriority = DisputePriority.valueOf(priority);

        dispute.setPriority(newPriority);
        Dispute saved = disputeRepository.save(dispute);

        logAuditAction(
                saved,
                admin,
                "PRIORITY_CHANGED",
                "Priority changed from " + oldPriority + " to " + newPriority,
                oldPriority.toString(),
                newPriority.toString(),
                null
        );

        return saved;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RETRIEVE DISPUTE DATA
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Get dispute detail with all information
     */
    public DisputeDetailDTO getDisputeDetail(UUID disputeId, Principal principal) {
        log.info("Fetching dispute detail: {}", disputeId);

        Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new RuntimeException("Dispute not found"));

        // Verify access
        if (principal != null) {
            User user = getActorFromPrincipal(principal);
            verifyDisputeAccess(dispute, user);
        }

        return buildDisputeDetailDTO(dispute);
    }

    /**
     * Get admin dispute list
     */
    public Page<DisputeListItemDTO> getAdminDisputeList(UUID adminId, Pageable pageable) {
        log.info("Fetching disputes for admin: {}", adminId);

        List<Dispute> disputes = disputeRepository.findByAssignedToAdminIdOrderByCreatedAtDesc(adminId);
        List<DisputeListItemDTO> items = disputes.stream()
                .map(this::buildDisputeListItemDTO)
                .collect(Collectors.toList());

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), items.size());
        List<DisputeListItemDTO> pageContent = items.subList(start, end);

        return new PageImpl<>(pageContent, pageable, items.size());
    }

    /**
     * Get unassigned disputes
     */
    public List<DisputeListItemDTO> getUnassignedDisputes() {
        log.info("Fetching unassigned disputes");

        return disputeRepository.findByAssignedToAdminIdIsNullOrderByCreatedAtDesc()
                .stream()
                .map(this::buildDisputeListItemDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get disputes involving user (client or worker)
     */
    public List<DisputeDetailDTO> getUserDisputes(Principal principal) {
        log.info("Fetching disputes for user");

        User user = getActorFromPrincipal(principal);
        return disputeRepository.findDisputesInvolvingUser(user.getId())
                .stream()
                .map(this::buildDisputeDetailDTO)
                .collect(Collectors.toList());
    }

    /**
     * Get audit trail for dispute
     */
    public List<AuditTrailDTO> getDisputeAuditTrail(UUID disputeId) {
        log.info("Fetching audit trail for dispute: {}", disputeId);

        return auditTrailRepository.findByDisputeIdOrderByCreatedAtDesc(disputeId)
                .stream()
                .map(this::buildAuditTrailDTO)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HELPER METHODS
    // ─────────────────────────────────────────────────────────────────────────

    private User getActorFromPrincipal(Principal principal) {
        return userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private void verifyDisputeAccess(Dispute dispute, User user) {
        boolean isClient = dispute.getJobRequest().getClient().getId().equals(user.getId());
        boolean isWorker = dispute.getJobRequest().getWorker() != null && 
                          dispute.getJobRequest().getWorker().getUser().getId().equals(user.getId());
        boolean isAdmin = user.getRole() == UserRole.ADMIN;

        if (!isClient && !isWorker && !isAdmin) {
            throw new RuntimeException("Unauthorized access to dispute");
        }
    }

    private void logAuditAction(Dispute dispute, User actor, String actionType, 
                               String description, String oldValue, String newValue, Object additionalData) {
        try {
            DisputeAuditTrail auditEntry = DisputeAuditTrail.builder()
                    .dispute(dispute)
                    .actor(actor)
                    .actionType(actionType)
                    .actionDescription(description)
                    .oldValue(oldValue)
                    .newValue(newValue)
                    .additionalData(additionalData != null ? 
                            objectMapper.valueToTree(additionalData) : null)
                    .build();
            auditTrailRepository.save(auditEntry);
        } catch (Exception e) {
            log.warn("Failed to log audit action for dispute {}: {}", dispute.getId(), e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DTO BUILDERS
    // ─────────────────────────────────────────────────────────────────────────

    private DisputeDetailDTO buildDisputeDetailDTO(Dispute dispute) {
        JobRequest job = dispute.getJobRequest();
        EscrowPayment payment = dispute.getEscrowPayment();

        return DisputeDetailDTO.builder()
                .id(dispute.getId())
                .jobId(job.getId())
                .escrowPaymentId(payment.getId())
                .filedByUsername(dispute.getFiledBy().getUsername())
                .filedByEmail(dispute.getFiledBy().getEmail())
                .filedByRole(dispute.getFiledBy().getRole().toString())
                .filedAt(dispute.getCreatedAt())
                .disputeReasonKey(dispute.getDisputeReasonKey())
                .disputeDescription(dispute.getDisputeDescription())
                .priority(dispute.getPriority())
                .status(dispute.getStatus())
                .bookingDetail(buildBookingDetailDTO(job))
                .escrowPaymentDetail(buildEscrowPaymentDetailDTO(payment))
                .evidence(evidenceRepository.findByDisputeIdOrderByCreatedAtDesc(dispute.getId())
                        .stream()
                        .map(this::buildEvidenceDetailDTO)
                        .collect(Collectors.toList()))
                .evidenceRequests(evidenceRequestRepository.findByDisputeIdOrderByCreatedAtDesc(dispute.getId())
                        .stream()
                        .map(this::buildEvidenceRequestDTO)
                        .collect(Collectors.toList()))
                .messages(messageRepository.findByDisputeIdAndIsAdminOnlyFalseOrderByCreatedAtAsc(dispute.getId())
                        .stream()
                        .map(this::buildMessageDetailDTO)
                        .collect(Collectors.toList()))
                .auditTrail(auditTrailRepository.findByDisputeIdOrderByCreatedAtDesc(dispute.getId())
                        .stream()
                        .map(this::buildAuditTrailDTO)
                        .collect(Collectors.toList()))
                .resolutionType(dispute.getResolutionType() != null ? 
                        dispute.getResolutionType().toString() : null)
                .clientResolutionAmount(dispute.getClientResolutionAmount())
                .workerResolutionAmount(dispute.getWorkerResolutionAmount())
                .adminResolutionReason(dispute.getAdminResolutionReason())
                .resolvedAt(dispute.getResolvedAt())
                .resolvedByAdminName(dispute.getResolvedByAdmin() != null ? 
                        dispute.getResolvedByAdmin().getUsername() : null)
                .clientProfile(buildUserProfileDTO(job.getClient()))
                .workerProfile(job.getWorker() != null ? 
                        buildUserProfileDTO(job.getWorker().getUser()) : null)
                .build();
    }

    public DisputeListItemDTO buildDisputeListItemDTO(Dispute dispute) {
        JobRequest job = dispute.getJobRequest();
        
        // Fetch evidence for this dispute
        List<DisputeEvidence> evidenceList = evidenceRepository.findByDisputeIdOrderByCreatedAtDesc(dispute.getId());
        List<DisputeListItemDTO.EvidenceDTO> evidenceDTOs = evidenceList.stream()
                .map(e -> DisputeListItemDTO.EvidenceDTO.builder()
                        .id(e.getId())
                        .fileName(e.getFileName())
                        .fileUrl(e.getFileUrl())
                        .fileType(e.getFileType())
                        .description(e.getDescription())
                        .uploadedBy(e.getUploadedBy() != null ? e.getUploadedBy().getFullName() : "Unknown")
                        .createdAt(e.getCreatedAt())
                        .build())
                .collect(java.util.stream.Collectors.toList());
        
        return DisputeListItemDTO.builder()
                .id(dispute.getId())
                .jobId(job.getId())
                .clientId(job.getClient() != null ? job.getClient().getId() : null)
                .workerId(job.getWorker() != null ? job.getWorker().getUser() != null ? job.getWorker().getUser().getId() : null : null)
                .clientName(job.getClient() != null ? job.getClient().getFullName() : "Unknown")
                .workerName(job.getWorker() != null ? job.getWorker().getUser() != null ? job.getWorker().getUser().getFullName() : "Unassigned" : "Unassigned")
                .filedByName(dispute.getFiledBy() != null ? dispute.getFiledBy().getFullName() : "Unknown")
                .disputeReasonKey(dispute.getDisputeReasonKey())
                .disputeDescription(dispute.getDisputeDescription())
                .priority(dispute.getPriority() != null ? dispute.getPriority().toString() : "MEDIUM")
                .status(dispute.getStatus() != null ? dispute.getStatus().toString() : "OPEN")
                .escrowAmount(dispute.getEscrowPayment() != null ? dispute.getEscrowPayment().getAmount() : null)
                .createdAt(dispute.getCreatedAt())
                .assignedToAdminName(dispute.getAssignedToAdmin() != null ?
                        dispute.getAssignedToAdmin().getUsername() : "Unassigned")
                .evidence(evidenceDTOs)
                .clientEvidence(job.getDisputeEvidence())
                .workerEvidence(job.getDisputeResponseEvidence())
                .clientEvidenceAttachmentUrl(job.getDisputeAttachmentUrl())
                .workerEvidenceAttachmentUrl(job.getDisputeResponseAttachmentUrl())
                .build();
    }

    private BookingDetailDTO buildBookingDetailDTO(JobRequest job) {
        return BookingDetailDTO.builder()
                .jobId(job.getId())
                .title(job.getTitle())
                .description(job.getDescription())
                .budget(job.getPriceQuote())
                .deadline(job.getDeadline())
                .startedAt(job.getStartedAt())
                .submittedAt(job.getSubmittedAt())
                .approvedAt(job.getApprovedAt())
                .status(job.getStatus().toString())
                .createdAt(job.getCreatedAt())
                .build();
    }

    private EscrowPaymentDetailDTO buildEscrowPaymentDetailDTO(EscrowPayment payment) {
        return EscrowPaymentDetailDTO.builder()
                .id(payment.getId())
                .amount(payment.getAmount())
                .platformFee(payment.getPlatformFee())
                .workerAmount(payment.getWorkerAmount())
                .status(payment.getStatus().toString())
                .mpesaReceiptNumber(payment.getMpesaReceiptNumber())
                .transactionDate(payment.getTransactionDate())
                .createdAt(payment.getCreatedAt())
                .message(payment.getMessage())
                .build();
    }

    private EvidenceDetailDTO buildEvidenceDetailDTO(DisputeEvidence evidence) {
        return EvidenceDetailDTO.builder()
                .id(evidence.getId())
                .fileName(evidence.getFileName())
                .fileUrl(evidence.getFileUrl())
                .fileType(evidence.getFileType())
                .description(evidence.getDescription())
                .uploadedByName(evidence.getUploadedBy().getFullName())
                .uploadedByRole(evidence.getUploadedBy().getRole().toString())
                .isAdminRequested(evidence.getIsAdminRequested())
                .adminEvidenceRequestType(evidence.getAdminEvidenceRequestType())
                .uploadedAt(evidence.getCreatedAt())
                .build();
    }

    private EvidenceRequestDTO buildEvidenceRequestDTO(DisputeEvidenceRequest request) {
        return EvidenceRequestDTO.builder()
                .id(request.getId())
                .requestType(request.getRequestType())
                .requestDescription(request.getRequestDescription())
                .requestStatus(request.getRequestStatus().toString())
                .requestedFromUserName(request.getRequestedFromUser().getFullName())
                .requestedByAdminName(request.getRequestedByAdmin().getUsername())
                .dueDate(request.getDueDate())
                .createdAt(request.getCreatedAt())
                .fulfilledAt(request.getFulfilledAt())
                .build();
    }

    private MessageDetailDTO buildMessageDetailDTO(DisputeMessage message) {
        return MessageDetailDTO.builder()
                .id(message.getId())
                .senderName(message.getSender().getFullName())
                .senderRole(message.getSender().getRole().toString())
                .messageType(message.getMessageType().toString())
                .messageText(message.getMessageText())
                .isAdminOnly(message.getIsAdminOnly())
                .sentAt(message.getCreatedAt())
                .isReadByClient(message.getIsReadByClient())
                .isReadByWorker(message.getIsReadByWorker())
                .isReadByAdmin(message.getIsReadByAdmin())
                .build();
    }

    private AuditTrailDTO buildAuditTrailDTO(DisputeAuditTrail audit) {
        return AuditTrailDTO.builder()
                .id(audit.getId())
                .actionType(audit.getActionType())
                .actionDescription(audit.getActionDescription())
                .actorName(audit.getActor().getFullName())
                .actorRole(audit.getActor().getRole().toString())
                .oldValue(audit.getOldValue())
                .newValue(audit.getNewValue())
                .additionalData(audit.getAdditionalData())
                .timestamp(audit.getCreatedAt())
                .build();
    }

    private UserProfileDTO buildUserProfileDTO(User user) {
        return UserProfileDTO.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(user.getRole().toString())
                .profilePictureUrl(user.getProfilePictureUrl())
                .build();
    }
}
