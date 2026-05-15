package com.kazikonnect.backend.features.worker;

import com.kazikonnect.backend.features.auth.User;
import com.kazikonnect.backend.features.auth.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import java.util.stream.Collectors;
import java.security.Principal;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
@SuppressWarnings("null")
public class JobRequestController {

    private final JobRequestRepository jobRequestRepository;
    private final UserRepository userRepository;
    private final WorkerProfileRepository workerProfileRepository;
    private final com.kazikonnect.backend.features.common.NotificationRepository notificationRepository;
    private final com.kazikonnect.backend.features.common.MessageRepository messageRepository;

    // READ: Get all job requests (Admin Oversight)
    @GetMapping("/all")
    @PreAuthorize("hasAuthority('Admin')")
    public ResponseEntity<?> getAllJobs() {
        return jobRequestRepository.findAll().stream()
                .map(JobRequestDTO::from)
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
                .map(JobRequestDTO::from)
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
                .map(JobRequestDTO::from)
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
            job.setStatus(targetStatus);
            JobRequest saved = jobRequestRepository.save(job);

            // LOGIC: If worker ACCEPTS the job
            if (targetStatus == JobStatus.ACCEPTED && workerOwner && oldStatus != JobStatus.ACCEPTED) {
                notificationRepository.save(com.kazikonnect.backend.features.common.Notification.builder()
                        .user(job.getClient())
                        .title("Job Accepted!")
                        .message(job.getWorker().getFullName() + " has accepted your job request.")
                        .type("SUCCESS")
                        .build());

                messageRepository.save(com.kazikonnect.backend.features.common.Message.builder()
                        .sender(actor)
                        .receiver(job.getClient())
                        .content("I've accepted your job request! I'll get started soon.")
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

            // LOGIC: If client marks as APPROVED (Payment Release)
            if (targetStatus == JobStatus.APPROVED && clientOwner && oldStatus != JobStatus.APPROVED) {
                // 1. Notification to Worker
                notificationRepository.save(com.kazikonnect.backend.features.common.Notification.builder()
                        .user(job.getWorker().getUser())
                        .title("Work Approved & Payment Released!")
                        .message(job.getClient().getFullName()
                                + " has approved your work. Payment is now available in your wallet.")
                        .type("SUCCESS")
                        .build());

                // 2. Automatic Message
                messageRepository.save(com.kazikonnect.backend.features.common.Message.builder()
                        .sender(actor)
                        .receiver(job.getWorker().getUser())
                        .content("Hi " + job.getWorker().getFullName() + ", I've approved the work. Great job!")
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
}
