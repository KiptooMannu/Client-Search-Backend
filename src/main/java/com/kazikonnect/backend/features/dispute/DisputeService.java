package com.kazikonnect.backend.features.dispute;

import com.kazikonnect.backend.features.auth.User;
import com.kazikonnect.backend.features.auth.UserRepository;
import com.kazikonnect.backend.features.auth.UserRole;
import com.kazikonnect.backend.features.common.Message;
import com.kazikonnect.backend.features.common.MessageRepository;
import com.kazikonnect.backend.features.common.Notification;
import com.kazikonnect.backend.features.common.NotificationRepository;
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
import org.springframework.messaging.simp.SimpMessagingTemplate;
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
    private final NotificationRepository notificationRepository;
    private final MessageRepository messageMessagingRepository;
    private final SimpMessagingTemplate messagingTemplate;

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

        // Update evidence request status to PROVIDED for pending requests from this user
        List<DisputeEvidenceRequest> pendingRequests = evidenceRequestRepository
                .findByDisputeIdAndRequestedFromUserIdAndRequestStatus(
                        disputeId,
                        actor.getId(),
                        EvidenceRequestStatus.PENDING
                );
        
        log.info("Found {} pending evidence requests for user {} on dispute {}", pendingRequests.size(), actor.getId(), disputeId);
        
        for (DisputeEvidenceRequest request : pendingRequests) {
            log.info("Updating evidence request {} from PENDING to PROVIDED", request.getId());
            request.setRequestStatus(EvidenceRequestStatus.PROVIDED);
            request.setFulfilledAt(LocalDateTime.now());
            evidenceRequestRepository.save(request);
        }

        // Check if all evidence requests for this dispute are now PROVIDED (excluding hidden ones)
        List<DisputeEvidenceRequest> allRequests = evidenceRequestRepository
                .findByDisputeIdAndHiddenFromAdminFalseOrderByCreatedAtDesc(disputeId);

        boolean allRequestsProvided = allRequests.stream()
                .allMatch(req -> req.getRequestStatus() == EvidenceRequestStatus.PROVIDED
                        || req.getRequestStatus() == EvidenceRequestStatus.SATISFIED);
        
        // If all requests are fulfilled and dispute is in AWAITING_EVIDENCE status, move to IN_REVIEW
        if (allRequestsProvided && dispute.getStatus() == DisputeStatus.AWAITING_EVIDENCE) {
            log.info("All evidence requests fulfilled for dispute {}, changing status from AWAITING_EVIDENCE to IN_REVIEW", disputeId);
            dispute.setStatus(DisputeStatus.IN_REVIEW);
            disputeRepository.save(dispute);
            
            // Log the status change
            logAuditAction(
                    dispute,
                    actor,
                    "STATUS_CHANGED",
                    "Dispute status changed from AWAITING_EVIDENCE to IN_REVIEW",
                    DisputeStatus.AWAITING_EVIDENCE.toString(),
                    DisputeStatus.IN_REVIEW.toString(),
                    null
            );
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

        // Notify the user that evidence is requested
        notificationRepository.save(Notification.builder()
                .user(requestedFromUser)
                .title("Evidence Requested")
                .message("An admin has requested evidence for your dispute. Please submit the requested evidence by " + savedRequest.getDueDate())
                .type("WARNING")
                .build());

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

        // Send resolution messages to both parties (temporarily disabled to isolate error)
        try {
            sendResolutionMessages(savedDispute, admin, resolutionType, clientAmount, workerAmount);
        } catch (Exception e) {
            log.error("Failed to send resolution messages: {}", e.getMessage(), e);
        }

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

    /**
     * Send resolution messages to both parties
     */
    private void sendResolutionMessages(Dispute dispute, User admin, ResolutionType resolutionType, Double clientAmount, Double workerAmount) {
        JobRequest job = dispute.getJobRequest();
        User client = job.getClient();
        User worker = job.getWorker().getUser();

        String resolutionMessage = buildResolutionMessage(resolutionType, clientAmount, workerAmount, dispute.getAdminResolutionReason());

        // Send message to client
        sendSystemMessage(admin, client, resolutionMessage);

        // Send message to worker
        sendSystemMessage(admin, worker, resolutionMessage);

        log.info("Resolution messages sent to client {} and worker {}", client.getId(), worker.getId());
    }

    /**
     * Build resolution message based on type
     */
    private String buildResolutionMessage(ResolutionType resolutionType, Double clientAmount, Double workerAmount, String reason) {
        StringBuilder message = new StringBuilder();
        message.append("Your dispute has been resolved. ");

        switch (resolutionType) {
            case FULL_REFUND_TO_CLIENT:
                message.append("Full refund issued to client. ");
                break;
            case FULL_PAYMENT_TO_WORKER:
                message.append("Full payment released to worker. ");
                break;
            case SPLIT:
                message.append(String.format("Split resolution: Worker receives KES %.2f, Client receives KES %.2f. ", workerAmount, clientAmount));
                break;
        }

        if (reason != null && !reason.trim().isEmpty()) {
            message.append("Reason: ").append(reason);
        }

        return message.toString();
    }

    /**
     * Send a system message from admin to user
     */
    private void sendSystemMessage(User admin, User receiver, String content) {
        try {
            Message message = new Message();
            message.setSender(admin);
            message.setReceiver(receiver);
            message.setContent(content);
            message.setAttachmentUrl(null);

            Message saved = messageMessagingRepository.save(message);

            // Send via WebSocket
            com.kazikonnect.backend.features.common.MessageDTO dto = com.kazikonnect.backend.features.common.MessageDTO.from(saved);
            messagingTemplate.convertAndSendToUser(
                    receiver.getId().toString(),
                    "/queue/messages",
                    dto
            );
        } catch (Exception e) {
            log.error("Failed to send WebSocket message to user {}: {}", receiver.getId(), e.getMessage());
        }
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

    /**
     * Hide evidence request from user view
     */
    public void hideEvidenceRequest(UUID requestId, Principal principal) {
        log.info("Hiding evidence request: {}", requestId);

        User user = getActorFromPrincipal(principal);

        DisputeEvidenceRequest request = evidenceRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Evidence request not found"));

        // Admins can hide from admin view, users can hide from their own view
        if (user.getRole() == UserRole.ADMIN) {
            request.setHiddenFromAdmin(true);
            log.info("Evidence request {} hidden from admin view by admin", requestId);
        } else {
            // Verify the user is the one the request was directed to
            if (!request.getRequestedFromUser().getId().equals(user.getId())) {
                throw new RuntimeException("You can only hide evidence requests directed at you");
            }
            request.setHiddenFromUser(true);
            log.info("Evidence request {} hidden from user view by {}", requestId, user.getUsername());
        }

        evidenceRequestRepository.save(request);
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
        User user = null;
        if (principal != null) {
            user = getActorFromPrincipal(principal);
            verifyDisputeAccess(dispute, user);
        }

        return buildDisputeDetailDTO(dispute, user);
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
                .map(dispute -> buildDisputeDetailDTO(dispute, user))
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

    private DisputeDetailDTO buildDisputeDetailDTO(Dispute dispute, User user) {
        JobRequest job = dispute.getJobRequest();
        EscrowPayment payment = dispute.getEscrowPayment();

        // Filter evidence requests based on user role
        List<DisputeEvidenceRequest> evidenceRequests;
        if (user != null && user.getRole() == UserRole.ADMIN) {
            // Admins see requests not hidden from admin
            evidenceRequests = evidenceRequestRepository.findByDisputeIdAndHiddenFromAdminFalseOrderByCreatedAtDesc(dispute.getId());
        } else if (user != null) {
            // Clients/workers see all dispute requests not hidden from user
            evidenceRequests = evidenceRequestRepository.findByDisputeIdAndHiddenFromUserFalseOrderByCreatedAtDesc(dispute.getId());
        } else {
            // No user context, show all requests not hidden from user
            evidenceRequests = evidenceRequestRepository.findByDisputeIdAndHiddenFromUserFalseOrderByCreatedAtDesc(dispute.getId());
        }

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
                .clientId(job.getClient() != null ? job.getClient().getId() : null)
                .workerId(job.getWorker() != null && job.getWorker().getUser() != null ? job.getWorker().getUser().getId() : null)
                .bookingDetail(buildBookingDetailDTO(job))
                .escrowPaymentDetail(buildEscrowPaymentDetailDTO(payment))
                .evidence(evidenceRepository.findByDisputeIdOrderByCreatedAtDesc(dispute.getId())
                        .stream()
                        .map(this::buildEvidenceDetailDTO)
                        .collect(Collectors.toList()))
                .evidenceRequests(evidenceRequests.stream()
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
                .hiddenFromAdmin(request.getHiddenFromAdmin())
                .hiddenFromUser(request.getHiddenFromUser())
                .requestedFromUser(buildUserProfileDTO(request.getRequestedFromUser()))
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
