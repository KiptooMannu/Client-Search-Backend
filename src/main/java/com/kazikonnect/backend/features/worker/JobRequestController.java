package com.kazikonnect.backend.features.worker;

import com.kazikonnect.backend.features.auth.User;
import com.kazikonnect.backend.features.auth.UserRepository;
import com.kazikonnect.backend.features.payment.PaymentService;
import com.kazikonnect.backend.features.payment.EscrowPayment;
import com.kazikonnect.backend.features.payment.EscrowPaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import java.util.Map;
import java.util.stream.Collectors;
import java.security.Principal;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("null")
public class JobRequestController {

    private final JobRequestRepository jobRequestRepository;
    private final UserRepository userRepository;
    private final WorkerProfileRepository workerProfileRepository;
    private final com.kazikonnect.backend.features.common.NotificationRepository notificationRepository;
    private final com.kazikonnect.backend.features.common.MessageRepository messageRepository;
    private final PaymentService paymentService;
    private final EscrowPaymentRepository escrowPaymentRepository;

    // READ: Get all job requests (Admin Oversight)
    @GetMapping("/all")
    @PreAuthorize("hasAuthority('Admin')")
    public ResponseEntity<?> getAllJobs() {
        return jobRequestRepository.findAll().stream()
                .map(this::toDtoWithPayment)
                .collect(Collectors.collectingAndThen(Collectors.toList(), ResponseEntity::ok));
    }

    // CREATE: Client requests a job
    @PostMapping("/request")
    @PreAuthorize("hasAuthority('Client')")
    public ResponseEntity<?> createJobRequest(
            @RequestParam UUID clientId,
            @RequestParam UUID workerUserId,
            @RequestBody JobRequest jobRequest,
            Principal principal) {

        User client = userRepository.findById(clientId).orElse(null);
        WorkerProfile worker = workerProfileRepository.findByUserId(workerUserId).orElse(null);
        User actor = userRepository.findByUsername(principal.getName()).orElse(null);

        if (actor == null) {
            return ResponseEntity.status(401).body("Unauthorized.");
        }
        if (client == null || worker == null) {
            return ResponseEntity.badRequest().body("Client or Worker not found.");
        }
        if (!actor.getId().equals(clientId)) {
            return ResponseEntity.status(403).body("Forbidden: clientId must match authenticated client.");
        }
        if (worker.getStatus() != WorkerStatus.APPROVED || !worker.isVisible()) {
            return ResponseEntity.badRequest().body("Worker is not available for hire.");
        }

        // Prevention of double hiring: Check for existing PENDING or ACCEPTED requests
        boolean alreadyRequested = jobRequestRepository.findAllByClientId(clientId).stream()
                .anyMatch(jr -> jr.getWorker().getId().equals(worker.getId()) &&
                        (jr.getStatus() == JobStatus.PENDING || jr.getStatus() == JobStatus.ACCEPTED));

        if (alreadyRequested) {
            return ResponseEntity.badRequest().body("You already have an active request with this professional.");
        }

        jobRequest.setClient(client);
        jobRequest.setWorker(worker);
        jobRequest.setStatus(JobStatus.PENDING);
        
        // Use client's offered jobPrice if provided, otherwise use worker's hourly rate
        Double offerPrice = jobRequest.getJobPrice();
        if (offerPrice != null && offerPrice > 0) {
            jobRequest.setTotalCost(offerPrice);
        } else {
            jobRequest.setTotalCost(worker.getHourlyRate());
        }

        JobRequest saved = jobRequestRepository.save(jobRequest);

        // Create notification for worker about new job offer
        if (worker.getUser() != null) {
            notificationRepository.save(com.kazikonnect.backend.features.common.Notification.builder()
                    .user(worker.getUser())
                    .title("📢 New Job Offer")
                    .message(client.getFullName() + " has sent you a job offer for " + jobRequest.getDescription() + ". Budget: KES " + jobRequest.getTotalCost())
                    .type("INFO")
                    .build());
        }

        return ResponseEntity.ok(JobRequestDTO.from(saved));
    }

    // POST: Worker submits counter-offer
    @PostMapping("/{jobId}/counter-offer")
    @PreAuthorize("hasAuthority('Worker')")
    @Transactional
    public ResponseEntity<?> submitCounterOffer(
            @PathVariable UUID jobId,
            @RequestParam Double counterPrice,
            Principal principal) {
        return jobRequestRepository.findById(jobId).map(job -> {
            User actor = userRepository.findByUsername(principal.getName()).orElse(null);
            if (actor == null) return ResponseEntity.status(401).body("Unauthorized.");

            if (job.getWorker() == null || job.getWorker().getUser() == null ||
                    !job.getWorker().getUser().getId().equals(actor.getId())) {
                return ResponseEntity.status(403).body("Forbidden: Only the assigned worker can submit a counter-offer.");
            }

            if (job.getStatus() != JobStatus.PENDING) {
                return ResponseEntity.badRequest().body("Counter-offers can only be submitted for pending jobs.");
            }

            if (counterPrice <= 0) {
                return ResponseEntity.badRequest().body("Counter-offer price must be greater than 0.");
            }

            job.setNegotiatedPrice(counterPrice);

            // Notify client about counter-offer
            if (job.getClient() != null) {
                notificationRepository.save(com.kazikonnect.backend.features.common.Notification.builder()
                        .user(job.getClient())
                        .title("💰 Counter-Offer Received")
                        .message(job.getWorker().getFullName() + " has submitted a counter-offer of KES " + counterPrice + " for your job request.")
                        .type("INFO")
                        .build());
            }

            JobRequest saved = jobRequestRepository.save(job);
            return ResponseEntity.ok(toDtoWithPayment(saved));
        }).orElse(ResponseEntity.notFound().build());
    }

    // POST: Client accepts counter-offer
    @PostMapping("/{jobId}/accept-counter-offer")
    @PreAuthorize("hasAuthority('Client')")
    @Transactional
    public ResponseEntity<?> acceptCounterOffer(
            @PathVariable UUID jobId,
            Principal principal) {
        return jobRequestRepository.findById(jobId).map(job -> {
            User actor = userRepository.findByUsername(principal.getName()).orElse(null);
            if (actor == null) return ResponseEntity.status(401).body("Unauthorized.");

            if (!job.getClient().getId().equals(actor.getId())) {
                return ResponseEntity.status(403).body("Forbidden: Only the client can accept the counter-offer.");
            }

            if (job.getNegotiatedPrice() == null) {
                return ResponseEntity.badRequest().body("No counter-offer to accept.");
            }

            if (job.getStatus() != JobStatus.PENDING) {
                return ResponseEntity.badRequest().body("Counter-offers can only be accepted for pending jobs.");
            }

            // Update job price to negotiated price
            job.setJobPrice(job.getNegotiatedPrice());
            job.setTotalCost(job.getNegotiatedPrice());

            // Notify worker about acceptance
            if (job.getWorker() != null && job.getWorker().getUser() != null) {
                notificationRepository.save(com.kazikonnect.backend.features.common.Notification.builder()
                        .user(job.getWorker().getUser())
                        .title("✅ Counter-Offer Accepted")
                        .message(job.getClient().getFullName() + " has accepted your counter-offer of KES " + job.getNegotiatedPrice() + ". You can now accept the job.")
                        .type("SUCCESS")
                        .build());
            }

            JobRequest saved = jobRequestRepository.save(job);
            return ResponseEntity.ok(JobRequestDTO.from(saved));
        }).orElse(ResponseEntity.notFound().build());
    }

    // POST: Client submits counter-offer (to respond to worker's counter or initial negotiation)
    @PostMapping("/{jobId}/client-counter-offer")
    @PreAuthorize("hasAuthority('Client')")
    @Transactional
    public ResponseEntity<?> submitClientCounterOffer(
            @PathVariable UUID jobId,
            @RequestParam Double counterPrice,
            Principal principal) {
        return jobRequestRepository.findById(jobId).map(job -> {
            User actor = userRepository.findByUsername(principal.getName()).orElse(null);
            if (actor == null) return ResponseEntity.status(401).body("Unauthorized.");

            if (!job.getClient().getId().equals(actor.getId())) {
                return ResponseEntity.status(403).body("Forbidden: Only the client can submit a counter-offer.");
            }

            if (job.getStatus() != JobStatus.PENDING) {
                return ResponseEntity.badRequest().body("Counter-offers can only be submitted for pending jobs.");
            }

            if (counterPrice <= 0) {
                return ResponseEntity.badRequest().body("Counter-offer price must be greater than 0.");
            }

            job.setNegotiatedPrice(counterPrice);

            // Notify worker about client's counter-offer
            if (job.getWorker() != null && job.getWorker().getUser() != null) {
                notificationRepository.save(com.kazikonnect.backend.features.common.Notification.builder()
                        .user(job.getWorker().getUser())
                        .title("💰 Counter-Offer from Client")
                        .message(job.getClient().getFullName() + " has submitted a counter-offer of KES " + counterPrice + " for your job request.")
                        .type("INFO")
                        .build());
            }

            JobRequest saved = jobRequestRepository.save(job);
            return ResponseEntity.ok(JobRequestDTO.from(saved));
        }).orElse(ResponseEntity.notFound().build());
    }

    // POST: Worker rejects client's counter-offer
    @PostMapping("/{jobId}/reject-counter-offer")
    @PreAuthorize("hasAuthority('Worker')")
    @Transactional
    public ResponseEntity<?> rejectCounterOffer(
            @PathVariable UUID jobId,
            Principal principal) {
        return jobRequestRepository.findById(jobId).map(job -> {
            User actor = userRepository.findByUsername(principal.getName()).orElse(null);
            if (actor == null) return ResponseEntity.status(401).body("Unauthorized.");

            if (job.getWorker() == null || job.getWorker().getUser() == null ||
                    !job.getWorker().getUser().getId().equals(actor.getId())) {
                return ResponseEntity.status(403).body("Forbidden: Only the assigned worker can reject a counter-offer.");
            }

            if (job.getNegotiatedPrice() == null) {
                return ResponseEntity.badRequest().body("No counter-offer to reject.");
            }

            if (job.getStatus() != JobStatus.PENDING) {
                return ResponseEntity.badRequest().body("Counter-offers can only be rejected for pending jobs.");
            }

            // Clear the negotiated price
            Double rejectedPrice = job.getNegotiatedPrice();
            job.setNegotiatedPrice(null);

            // Notify client about rejection
            if (job.getClient() != null) {
                notificationRepository.save(com.kazikonnect.backend.features.common.Notification.builder()
                        .user(job.getClient())
                        .title("❌ Counter-Offer Rejected")
                        .message(job.getWorker().getFullName() + " has rejected your counter-offer of KES " + rejectedPrice + ". You can submit a new offer.")
                        .type("INFO")
                        .build());
            }

            JobRequest saved = jobRequestRepository.save(job);
            return ResponseEntity.ok(JobRequestDTO.from(saved));
        }).orElse(ResponseEntity.notFound().build());
    }

    // READ: Get all job requests for a client
    @GetMapping("/client/{clientId}")
    @PreAuthorize("hasAuthority('Client') or hasAuthority('Admin')")
    public ResponseEntity<?> getClientJobs(@PathVariable UUID clientId, Principal principal) {
        User actor = userRepository.findByUsername(principal.getName()).orElse(null);
        if (actor == null)
            return ResponseEntity.status(401).body("Unauthorized.");
        boolean admin = actor.getRole() == com.kazikonnect.backend.features.auth.UserRole.ADMIN;
        if (!admin && !actor.getId().toString().equals(clientId.toString())) {
            return ResponseEntity.status(403).body("Forbidden.");
        }
        return jobRequestRepository.findAllByClientId(clientId).stream()
                .map(this::toDtoWithPayment)
                .collect(Collectors.collectingAndThen(Collectors.toList(), ResponseEntity::ok));
    }

    // READ: Get all job requests for a worker (using userId)
    @GetMapping("/worker/user/{userId}")
    @PreAuthorize("hasAuthority('Worker') or hasAuthority('Admin')")
    public ResponseEntity<?> getWorkerJobs(@PathVariable UUID userId, Principal principal) {
        User actor = userRepository.findByUsername(principal.getName()).orElse(null);
        if (actor == null)
            return ResponseEntity.status(401).body("Unauthorized.");
        boolean admin = actor.getRole() == com.kazikonnect.backend.features.auth.UserRole.ADMIN;
        WorkerProfile worker = workerProfileRepository.findByUserId(userId).orElse(null);
        if (worker == null) {
            // Return empty list instead of 404 to avoid frontend errors during initialization
            return ResponseEntity.ok(new java.util.ArrayList<>());
        }
        if (!admin && (worker.getUser() == null
                || !worker.getUser().getId().toString().equals(actor.getId().toString()))) {
            return ResponseEntity.status(403).body("Forbidden.");
        }
        return jobRequestRepository.findAllByWorkerId(worker.getId()).stream()
                .map(this::toDtoWithPayment)
                .collect(Collectors.collectingAndThen(Collectors.toList(), ResponseEntity::ok));
    }

    // UPDATE: Update job status (e.g. ACCEPTED, REJECTED, COMPLETED, CANCELLED)
    @PutMapping("/{jobId}/status")
    @PreAuthorize("hasAuthority('Client') or hasAuthority('Worker') or hasAuthority('Admin')")
    public ResponseEntity<?> updateJobStatus(@PathVariable UUID jobId, @RequestParam JobStatus status,
            Principal principal) {
        return jobRequestRepository.findById(jobId).map(job -> {
            User actor = userRepository.findByUsername(principal.getName()).orElse(null);
            if (actor == null)
                return ResponseEntity.status(401).body("Unauthorized.");
            boolean admin = actor.getRole() == com.kazikonnect.backend.features.auth.UserRole.ADMIN;
            boolean clientOwner = job.getClient() != null
                    && job.getClient().getId().toString().equals(actor.getId().toString());
            boolean workerOwner = job.getWorker() != null && job.getWorker().getUser() != null
                    && job.getWorker().getUser().getId().toString().equals(actor.getId().toString());
            if (!admin && !clientOwner && !workerOwner) {
                return ResponseEntity.status(403).body("Forbidden.");
            }
            JobStatus oldStatus = job.getStatus();
            JobStatus targetStatus = status;
            // Normalize REVISION to REVISION_REQUESTED
            if (targetStatus == JobStatus.REVISION) {
                targetStatus = JobStatus.REVISION_REQUESTED;
            }

            // Client/admin approval releases escrow to the worker wallet.
            if (targetStatus == JobStatus.APPROVED) {
                if (!clientOwner && !admin) {
                    return ResponseEntity.status(403).body("Only the client or an admin can approve delivered work.");
                }

                try {
                    paymentService.releaseEscrow(jobId, principal);
                } catch (Exception e) {
                    log.warn("Work approval failed for job {}: {}", jobId, e.getMessage(), e);
                    return ResponseEntity.badRequest().body(
                            "Cannot approve work: " + (e.getMessage() != null ? e.getMessage() : "Payment is not ready for release."));
                }

                JobRequest updatedJob = jobRequestRepository.findById(jobId).orElse(job);

                if (oldStatus != JobStatus.APPROVED) {
                    try {
                        if (updatedJob.getWorker() != null && updatedJob.getWorker().getUser() != null) {
                            String clientName = updatedJob.getClient() != null ? updatedJob.getClient().getFullName() : "Client";
                            String workerName = safeWorkerName(updatedJob.getWorker());

                            notificationRepository.save(com.kazikonnect.backend.features.common.Notification.builder()
                                    .user(updatedJob.getWorker().getUser())
                                    .title("Work Approved & Payment Released!")
                                    .message(clientName + " has approved your work. Payment is now available in your wallet.")
                                    .type("SUCCESS")
                                    .build());

                            messageRepository.save(com.kazikonnect.backend.features.common.Message.builder()
                                    .sender(actor)
                                    .receiver(updatedJob.getWorker().getUser())
                                    .content("Hi " + workerName + ", I've approved the work. Great job!")
                                    .isRead(false)
                                    .build());
                        }
                    } catch (Exception notifyEx) {
                        log.warn("Approval succeeded for job {} but notification delivery failed: {}", jobId, notifyEx.getMessage());
                    }
                }

                try {
                    return ResponseEntity.ok(toDtoWithPayment(updatedJob));
                } catch (Exception dtoEx) {
                    log.warn("Approval succeeded for job {} but response mapping failed: {}", jobId, dtoEx.getMessage());
                    return ResponseEntity.ok(JobRequestDTO.from(updatedJob));
                }
            }

            job.setStatus(targetStatus);
            JobRequest saved = jobRequestRepository.save(job);

            // LOGIC: If worker ACCEPTS the job
            if (targetStatus == JobStatus.ACCEPTED && workerOwner && oldStatus != JobStatus.ACCEPTED) {
                // If there's a negotiated price, update totalCost to match
                if (job.getNegotiatedPrice() != null && job.getNegotiatedPrice() > 0) {
                    job.setJobPrice(job.getNegotiatedPrice());
                    job.setTotalCost(job.getNegotiatedPrice());
                    
                    // Notify client about negotiated price acceptance
                    notificationRepository.save(com.kazikonnect.backend.features.common.Notification.builder()
                            .user(job.getClient())
                            .title("✅ Negotiated Price Accepted")
                            .message(job.getWorker().getFullName() + " has accepted your counter-offer of KES " + job.getNegotiatedPrice() + ". The job price is now updated.")
                            .type("SUCCESS")
                            .build());
                }
                
                notificationRepository.save(com.kazikonnect.backend.features.common.Notification.builder()
                        .user(job.getClient())
                        .title("Job Accepted — Payment Required!")
                        .message(job.getWorker().getFullName()
                                + " accepted your job request. Please go to My Bookings and make the M-Pesa payment to start the work.")
                        .type("SUCCESS")
                        .build());

                messageRepository.save(com.kazikonnect.backend.features.common.Message.builder()
                        .sender(actor)
                        .receiver(job.getClient())
                        .content("Hi " + job.getClient().getFullName() + "! I've accepted your job request. " +
                                 "Please make the payment via M-Pesa in your Bookings page so I can get started. " +
                                 "Work will begin as soon as payment is confirmed in escrow.")
                        .isRead(false)
                        .build());
            }

            // LOGIC: If worker REJECTS the job
            if (targetStatus == JobStatus.REJECTED && workerOwner && oldStatus != JobStatus.REJECTED) {
                notificationRepository.save(com.kazikonnect.backend.features.common.Notification.builder()
                        .user(job.getClient())
                        .title("Job Declined")
                        .message(job.getWorker().getFullName() + " is unable to take your job request at this time.")
                        .type("INFO")
                        .build());
            }

            // LOGIC: If worker starts work (IN_PROGRESS)
            if (targetStatus == JobStatus.IN_PROGRESS && workerOwner && oldStatus != JobStatus.IN_PROGRESS) {
                notificationRepository.save(com.kazikonnect.backend.features.common.Notification.builder()
                        .user(job.getClient())
                        .title("Work Started")
                        .message(job.getWorker().getFullName() + " has started working on your project.")
                        .type("INFO")
                        .build());
            }

            // LOGIC: If worker marks as SUBMITTED (Work Delivered)
            if (targetStatus == JobStatus.SUBMITTED && workerOwner && oldStatus != JobStatus.SUBMITTED) {
                // 1. Notification to Client
                notificationRepository.save(com.kazikonnect.backend.features.common.Notification.builder()
                        .user(job.getClient())
                        .title("Work Delivered!")
                        .message(job.getWorker().getFullName()
                                + " has submitted the work. Please review and approve to release payment.")
                        .type("SUCCESS")
                        .build());

                // 2. Automatic Message
                messageRepository.save(com.kazikonnect.backend.features.common.Message.builder()
                        .sender(actor)
                        .receiver(job.getClient())
                        .content("Hi " + job.getClient().getFullName()
                                + ", I have delivered the work. Please review it at your convenience!")
                        .isRead(false)
                        .build());
            }

            // LOGIC: If client requests REVISION
            if (targetStatus == JobStatus.REVISION_REQUESTED && clientOwner
                    && oldStatus != JobStatus.REVISION_REQUESTED) {
                notificationRepository.save(com.kazikonnect.backend.features.common.Notification.builder()
                        .user(job.getWorker().getUser())
                        .title("Revision Requested")
                        .message(job.getClient().getFullName() + " has requested changes to the submitted work.")
                        .type("WARNING")
                        .build());
            }

            // LOGIC: If client opens a DISPUTE
            if (targetStatus == JobStatus.DISPUTED && clientOwner && oldStatus != JobStatus.DISPUTED) {
                notificationRepository.save(com.kazikonnect.backend.features.common.Notification.builder()
                        .user(job.getWorker().getUser())
                        .title("Dispute Opened")
                        .message(job.getClient().getFullName() + " has opened a dispute regarding this job.")
                        .type("DANGER")
                        .build());
            }

            // LOGIC: If job is CANCELLED
            if (targetStatus == JobStatus.CANCELLED && oldStatus != JobStatus.CANCELLED) {
                paymentService.refundEscrowBySystem(jobId, "Payment refunded due to job cancellation.");

                User recipient = clientOwner ? job.getWorker().getUser() : job.getClient();
                notificationRepository.save(com.kazikonnect.backend.features.common.Notification.builder()
                        .user(recipient)
                        .title("Job Cancelled")
                        .message("The job request has been cancelled by the " + (clientOwner ? "client" : "worker")
                                + ".")
                        .type("INFO")
                        .build());
            }

            return ResponseEntity.ok(JobRequestDTO.from(saved));
        }).orElse(ResponseEntity.notFound().build());
    }

    // POST: Client opens a dispute
    @PostMapping("/{jobId}/dispute")
    @PreAuthorize("hasAuthority('Client')")
    @Transactional
    public ResponseEntity<?> openDispute(
            @PathVariable UUID jobId,
            @RequestParam String reason,
            @RequestParam(required = false) String evidence,
            @RequestParam(required = false) String attachmentUrl,
            Principal principal) {
        return jobRequestRepository.findById(jobId).map(job -> {
            User actor = userRepository.findByUsername(principal.getName()).orElse(null);
            if (actor == null) return ResponseEntity.status(401).body("Unauthorized.");

            if (!job.getClient().getId().equals(actor.getId())) {
                return ResponseEntity.status(403).body("Forbidden: Only the client can open a dispute.");
            }

            if (job.getStatus() == JobStatus.DISPUTED) {
                return ResponseEntity.badRequest().body("Dispute already open for this job.");
            }

            job.setStatus(JobStatus.DISPUTED);
            job.setDisputedAt(java.time.LocalDateTime.now());
            job.setDisputeReason(reason);
            job.setDisputeEvidence(evidence);
            job.setDisputeAttachmentUrl(attachmentUrl);

            JobRequest saved = jobRequestRepository.save(job);

            notificationRepository.save(com.kazikonnect.backend.features.common.Notification.builder()
                    .user(job.getWorker().getUser())
                    .title("Dispute Opened")
                    .message(job.getClient().getFullName() + " has opened a dispute regarding this job: " + reason)
                    .type("DANGER")
                    .build());

            return ResponseEntity.ok(JobRequestDTO.from(saved));
        }).orElse(ResponseEntity.notFound().build());
    }

    // POST: Worker responds to a dispute
    @PostMapping("/{jobId}/dispute/respond")
    @PreAuthorize("hasAuthority('Worker')")
    @Transactional
    public ResponseEntity<?> respondToDispute(
            @PathVariable UUID jobId,
            @RequestParam String response,
            @RequestParam(required = false) String evidence,
            @RequestParam(required = false) String attachmentUrl,
            Principal principal) {
        return jobRequestRepository.findById(jobId).map(job -> {
            User actor = userRepository.findByUsername(principal.getName()).orElse(null);
            if (actor == null) return ResponseEntity.status(401).body("Unauthorized.");

            if (job.getWorker() == null || job.getWorker().getUser() == null ||
                    !job.getWorker().getUser().getId().equals(actor.getId())) {
                return ResponseEntity.status(403).body("Forbidden: Only the assigned worker can respond.");
            }

            if (job.getStatus() != JobStatus.DISPUTED) {
                return ResponseEntity.badRequest().body("No open dispute on this job to respond to.");
            }

            job.setDisputeResponse(response);
            job.setDisputeResponseEvidence(evidence);
            job.setDisputeResponseAttachmentUrl(attachmentUrl);

            JobRequest saved = jobRequestRepository.save(job);

            notificationRepository.save(com.kazikonnect.backend.features.common.Notification.builder()
                    .user(job.getClient())
                    .title("Dispute Response Received")
                    .message(job.getWorker().getFullName() + " has responded to your dispute.")
                    .type("INFO")
                    .build());

            return ResponseEntity.ok(JobRequestDTO.from(saved));
        }).orElse(ResponseEntity.notFound().build());
    }

    // POST: Admin resolves dispute
    @PostMapping("/{jobId}/admin/resolve")
    @PreAuthorize("hasAuthority('Admin')")
    @Transactional
    public ResponseEntity<?> resolveDispute(
            @PathVariable UUID jobId,
            @RequestParam String decisionReason,
            @RequestParam(required = false) String evidenceNotes,
            @RequestParam double workerPartialAmount,
            @RequestParam double clientPartialAmount,
            Principal principal) {
        return jobRequestRepository.findById(jobId).map(job -> {
            User actor = userRepository.findByUsername(principal.getName()).orElse(null);
            if (actor == null) return ResponseEntity.status(401).body("Unauthorized.");

            if (job.getStatus() != JobStatus.DISPUTED) {
                return ResponseEntity.badRequest().body("Job is not in DISPUTED status.");
            }

            if (workerPartialAmount < 0 || clientPartialAmount < 0) {
                return ResponseEntity.badRequest().body("Amounts cannot be negative.");
            }

            try {
                paymentService.partialRefundEscrow(jobId, workerPartialAmount, clientPartialAmount, decisionReason);
            } catch (Exception e) {
                return ResponseEntity.badRequest().body("Failed to process dispute refund: " + e.getMessage());
            }

            job.setAdminDecisionReason(decisionReason);
            job.setAdminEvidenceNotes(evidenceNotes);
            job.setWorkerPartialAmount(workerPartialAmount);
            job.setClientPartialAmount(clientPartialAmount);
            job.setResolvedAt(java.time.LocalDateTime.now());
            job.setStatus(JobStatus.COMPLETED);

            JobRequest saved = jobRequestRepository.save(job);

            // Notify client
            if (job.getClient() != null) {
                notificationRepository.save(com.kazikonnect.backend.features.common.Notification.builder()
                        .user(job.getClient())
                        .title("Dispute Resolved")
                        .message("Admin has resolved the dispute. Client payout: KES " + clientPartialAmount + ". Reason: " + decisionReason)
                        .type("INFO")
                        .build());
            }

            // Notify worker
            if (job.getWorker() != null && job.getWorker().getUser() != null) {
                notificationRepository.save(com.kazikonnect.backend.features.common.Notification.builder()
                        .user(job.getWorker().getUser())
                        .title("Dispute Resolved")
                        .message("Admin has resolved the dispute. Worker payout: KES " + workerPartialAmount + ". Reason: " + decisionReason)
                        .type("INFO")
                        .build());
            }

            return ResponseEntity.ok(JobRequestDTO.from(saved));
        }).orElse(ResponseEntity.notFound().build());
    }

    // DELETE: Delete a job request
    @DeleteMapping("/{jobId}")
    @PreAuthorize("hasAuthority('Worker') or hasAuthority('Admin')")
    public ResponseEntity<?> deleteJob(@PathVariable UUID jobId, Principal principal) {
        return jobRequestRepository.findById(jobId).map(job -> {
            User actor = userRepository.findByUsername(principal.getName()).orElse(null);
            if (actor == null)
                return ResponseEntity.status(401).body("Unauthorized.");

            boolean admin = actor.getRole() == com.kazikonnect.backend.features.auth.UserRole.ADMIN;
            boolean workerOwner = job.getWorker() != null && job.getWorker().getUser() != null
                    && job.getWorker().getUser().getId().toString().equals(actor.getId().toString());

            if (!admin && !workerOwner) {
                return ResponseEntity.status(403).body("Forbidden: You can only delete your own assigned jobs.");
            }

            jobRequestRepository.delete(job);
            return ResponseEntity.ok(java.util.Map.of("message", "Job request removed successfully."));
        }).orElse(ResponseEntity.notFound().build());
    }

    // PUT: Client cancels hire (before funding)
    @PutMapping("/{jobId}/cancel")
    @PreAuthorize("hasAuthority('Client')")
    @Transactional
    public ResponseEntity<?> cancelHire(
            @PathVariable UUID jobId,
            @RequestBody Map<String, String> payload,
            Principal principal) {
        return jobRequestRepository.findById(jobId).map(job -> {
            User actor = userRepository.findByUsername(principal.getName()).orElse(null);
            if (actor == null)
                return ResponseEntity.status(401).body("Unauthorized.");

            if (!job.getClient().getId().equals(actor.getId())) {
                return ResponseEntity.status(403).body("Forbidden: Only the client can cancel the hire.");
            }

            // Can only cancel if job is in ACCEPTED or AWAITING_FUNDING status and not funded
            if (job.getStatus() != JobStatus.ACCEPTED && job.getStatus() != JobStatus.AWAITING_FUNDING) {
                return ResponseEntity.badRequest().body("Job can only be cancelled before funding.");
            }

            if (job.getEscrowFunded()) {
                return ResponseEntity.badRequest().body("Cannot cancel hire after escrow has been funded.");
            }

            job.setStatus(JobStatus.CLIENT_CANCELLED);
            job.setCancellationReason(payload.get("cancellationReason"));
            job.setCancelledBy(UUID.fromString(payload.get("cancelledBy")));
            job.setCancelledAt(java.time.LocalDateTime.parse(payload.get("cancelledAt")));

            JobRequest saved = jobRequestRepository.save(job);

            // Notify worker about cancellation
            if (job.getWorker() != null && job.getWorker().getUser() != null) {
                notificationRepository.save(com.kazikonnect.backend.features.common.Notification.builder()
                        .user(job.getWorker().getUser())
                        .title("Hire Cancelled")
                        .message(job.getClient().getFullName() + " has cancelled the hire. Reason: " + payload.get("cancellationReason"))
                        .type("INFO")
                        .build());
            }

            return ResponseEntity.ok(JobRequestDTO.from(saved));
        }).orElse(ResponseEntity.notFound().build());
    }

    // PUT: Worker withdraws acceptance (before funding)
    @PutMapping("/{jobId}/withdraw")
    @PreAuthorize("hasAuthority('Worker')")
    @Transactional
    public ResponseEntity<?> withdrawAcceptance(
            @PathVariable UUID jobId,
            @RequestBody Map<String, String> payload,
            Principal principal) {
        return jobRequestRepository.findById(jobId).map(job -> {
            User actor = userRepository.findByUsername(principal.getName()).orElse(null);
            if (actor == null)
                return ResponseEntity.status(401).body("Unauthorized.");

            if (job.getWorker() == null || job.getWorker().getUser() == null ||
                    !job.getWorker().getUser().getId().equals(actor.getId())) {
                return ResponseEntity.status(403).body("Forbidden: Only the assigned worker can withdraw acceptance.");
            }

            // Can only withdraw if job is in ACCEPTED or AWAITING_FUNDING status and not funded
            if (job.getStatus() != JobStatus.ACCEPTED && job.getStatus() != JobStatus.AWAITING_FUNDING) {
                return ResponseEntity.badRequest().body("Acceptance can only be withdrawn before funding.");
            }

            if (job.getEscrowFunded()) {
                return ResponseEntity.badRequest().body("Cannot withdraw acceptance after escrow has been funded.");
            }

            job.setStatus(JobStatus.WORKER_CANCELLED);
            job.setCancellationReason(payload.get("cancellationReason"));
            job.setCancelledBy(UUID.fromString(payload.get("cancelledBy")));
            job.setCancelledAt(java.time.LocalDateTime.parse(payload.get("cancelledAt")));

            JobRequest saved = jobRequestRepository.save(job);

            // Notify client about withdrawal
            if (job.getClient() != null) {
                notificationRepository.save(com.kazikonnect.backend.features.common.Notification.builder()
                        .user(job.getClient())
                        .title("Worker Withdrew Acceptance")
                        .message(job.getWorker().getFullName() + " has withdrawn their acceptance. Reason: " + payload.get("cancellationReason"))
                        .type("INFO")
                        .build());
            }

            return ResponseEntity.ok(JobRequestDTO.from(saved));
        }).orElse(ResponseEntity.notFound().build());
    }

    // PUT: System expires job (due to lack of funding)
    @PutMapping("/{jobId}/expire")
    @Transactional
    public ResponseEntity<?> expireJob(
            @PathVariable UUID jobId,
            @RequestBody Map<String, String> payload) {
        return jobRequestRepository.findById(jobId).map(job -> {
            // Can only expire if job is in ACCEPTED or AWAITING_FUNDING status and not funded
            if (job.getStatus() != JobStatus.ACCEPTED && job.getStatus() != JobStatus.AWAITING_FUNDING) {
                return ResponseEntity.badRequest().body("Job can only be expired if awaiting funding.");
            }

            if (job.getEscrowFunded()) {
                return ResponseEntity.badRequest().body("Cannot expire job after escrow has been funded.");
            }

            job.setStatus(JobStatus.EXPIRED);
            job.setCancellationReason(payload.get("cancellationReason"));
            job.setCancelledBy(UUID.fromString(payload.get("cancelledBy")));
            job.setCancelledAt(java.time.LocalDateTime.parse(payload.get("cancelledAt")));

            JobRequest saved = jobRequestRepository.save(job);

            // Notify both client and worker about expiry
            if (job.getClient() != null) {
                notificationRepository.save(com.kazikonnect.backend.features.common.Notification.builder()
                        .user(job.getClient())
                        .title("Job Expired")
                        .message("The job has expired due to lack of funding within the allowed time period.")
                        .type("WARNING")
                        .build());
            }

            if (job.getWorker() != null && job.getWorker().getUser() != null) {
                notificationRepository.save(com.kazikonnect.backend.features.common.Notification.builder()
                        .user(job.getWorker().getUser())
                        .title("Job Expired")
                        .message("The job has expired due to lack of funding within the allowed time period.")
                        .type("WARNING")
                        .build());
            }

            return ResponseEntity.ok(JobRequestDTO.from(saved));
        }).orElse(ResponseEntity.notFound().build());
    }

    private JobRequestDTO toDtoWithPayment(JobRequest job) {
        EscrowPayment payment = escrowPaymentRepository.findTopByJobRequestIdOrderByCreatedAtDesc(job.getId()).orElse(null);
        return JobRequestDTO.from(job, payment);
    }

    private String safeWorkerName(WorkerProfile worker) {
        if (worker == null) {
            return "Worker";
        }
        if (worker.getFullName() != null && !worker.getFullName().isBlank()) {
            return worker.getFullName();
        }
        if (worker.getUser() != null) {
            return worker.getUser().getFullName();
        }
        return "Worker";
    }
}
