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
    
    // READ: Get all job requests (Admin Oversight)
    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getAllJobs() {
        return jobRequestRepository.findAll().stream()
                .map(JobRequestDTO::from)
                .collect(Collectors.collectingAndThen(Collectors.toList(), ResponseEntity::ok));
    }

    // CREATE: Client requests a job
    @PostMapping("/request")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<?> createJobRequest(
            @RequestParam UUID clientId,
            @RequestParam UUID workerProfileId,
            @RequestBody JobRequest jobRequest,
            Principal principal) {
        
        User client = userRepository.findById(clientId).orElse(null);
        WorkerProfile worker = workerProfileRepository.findById(workerProfileId).orElse(null);
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
                .anyMatch(jr -> jr.getWorker().getId().equals(workerProfileId) && 
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
    @PreAuthorize("hasRole('CLIENT') or hasRole('ADMIN')")
    public ResponseEntity<?> getClientJobs(@PathVariable UUID clientId, Principal principal) {
        User actor = userRepository.findByUsername(principal.getName()).orElse(null);
        if (actor == null) return ResponseEntity.status(401).body("Unauthorized.");
        boolean admin = actor.getRole() == com.kazikonnect.backend.features.auth.UserRole.ADMIN;
        if (!admin && !actor.getId().equals(clientId)) {
            return ResponseEntity.status(403).body("Forbidden.");
        }
        return jobRequestRepository.findAllByClientId(clientId).stream()
                .map(JobRequestDTO::from)
                .collect(Collectors.collectingAndThen(Collectors.toList(), ResponseEntity::ok));
    }

    // READ: Get all job requests for a worker
    @GetMapping("/worker/{workerProfileId}")
    @PreAuthorize("hasRole('WORKER') or hasRole('ADMIN')")
    public ResponseEntity<?> getWorkerJobs(@PathVariable UUID workerProfileId, Principal principal) {
        User actor = userRepository.findByUsername(principal.getName()).orElse(null);
        if (actor == null) return ResponseEntity.status(401).body("Unauthorized.");
        boolean admin = actor.getRole() == com.kazikonnect.backend.features.auth.UserRole.ADMIN;
        WorkerProfile worker = workerProfileRepository.findById(workerProfileId).orElse(null);
        if (worker == null) return ResponseEntity.notFound().build();
        if (!admin && (worker.getUser() == null || !worker.getUser().getId().equals(actor.getId()))) {
            return ResponseEntity.status(403).body("Forbidden.");
        }
        return jobRequestRepository.findAllByWorkerId(workerProfileId).stream()
                .map(JobRequestDTO::from)
                .collect(Collectors.collectingAndThen(Collectors.toList(), ResponseEntity::ok));
    }

    // UPDATE: Update job status (e.g. ACCEPTED, REJECTED, COMPLETED, CANCELLED)
    @PutMapping("/{jobId}/status")
    @PreAuthorize("hasAnyRole('CLIENT', 'WORKER', 'ADMIN')")
    public ResponseEntity<?> updateJobStatus(@PathVariable UUID jobId, @RequestParam JobStatus status, Principal principal) {
        return jobRequestRepository.findById(jobId).map(job -> {
            User actor = userRepository.findByUsername(principal.getName()).orElse(null);
            if (actor == null) return ResponseEntity.status(401).body("Unauthorized.");
            boolean admin = actor.getRole() == com.kazikonnect.backend.features.auth.UserRole.ADMIN;
            boolean clientOwner = job.getClient() != null && job.getClient().getId().equals(actor.getId());
            boolean workerOwner = job.getWorker() != null && job.getWorker().getUser() != null
                    && job.getWorker().getUser().getId().equals(actor.getId());
            if (!admin && !clientOwner && !workerOwner) {
                return ResponseEntity.status(403).body("Forbidden.");
            }
            job.setStatus(status);
            return ResponseEntity.ok(JobRequestDTO.from(jobRequestRepository.save(job)));
        }).orElse(ResponseEntity.notFound().build());
    }

    // DELETE: Delete a job request
    @DeleteMapping("/{jobId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteJob(@PathVariable UUID jobId) {
        if (!jobRequestRepository.existsById(jobId)) {
            return ResponseEntity.notFound().build();
        }
        jobRequestRepository.deleteById(jobId);
        return ResponseEntity.ok("Job deleted successfully.");
    }
}
