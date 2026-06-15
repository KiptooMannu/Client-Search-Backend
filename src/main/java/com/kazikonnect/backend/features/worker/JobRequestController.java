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
        jobRequest.setTotalCost(worker.getHourlyRate());

        JobRequest saved = jobRequestRepository.save(jobRequest);
        return ResponseEntity.ok(JobRequestDTO.from(saved));
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
