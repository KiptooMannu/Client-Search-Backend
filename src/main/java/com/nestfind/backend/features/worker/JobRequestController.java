package com.nestfind.backend.features.worker;

import com.nestfind.backend.features.auth.User;
import com.nestfind.backend.features.auth.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
@SuppressWarnings("null")
public class JobRequestController {

    private final JobRequestRepository jobRequestRepository;
    private final UserRepository userRepository;
    private final WorkerProfileRepository workerProfileRepository;

    // CREATE: Client requests a job
    @PostMapping("/request")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<?> createJobRequest(
            @RequestParam UUID clientId,
            @RequestParam UUID workerProfileId,
            @RequestBody JobRequest jobRequest) {
        
        User client = userRepository.findById(clientId).orElse(null);
        WorkerProfile worker = workerProfileRepository.findById(workerProfileId).orElse(null);

        if (client == null || worker == null) {
            return ResponseEntity.badRequest().body("Client or Worker not found.");
        }

        jobRequest.setClient(client);
        jobRequest.setWorker(worker);
        jobRequest.setStatus(JobStatus.PENDING);

        JobRequest saved = jobRequestRepository.save(jobRequest);
        return ResponseEntity.ok(JobRequestDTO.from(saved));
    }

    // READ: Get all job requests for a client
    @GetMapping("/client/{clientId}")
    @PreAuthorize("hasRole('CLIENT') or hasRole('ADMIN')")
    public List<JobRequestDTO> getClientJobs(@PathVariable UUID clientId) {
        return jobRequestRepository.findAllByClientId(clientId).stream()
                .map(JobRequestDTO::from)
                .collect(Collectors.toList());
    }

    // READ: Get all job requests for a worker
    @GetMapping("/worker/{workerProfileId}")
    @PreAuthorize("hasRole('WORKER') or hasRole('ADMIN')")
    public List<JobRequestDTO> getWorkerJobs(@PathVariable UUID workerProfileId) {
        return jobRequestRepository.findAllByWorkerId(workerProfileId).stream()
                .map(JobRequestDTO::from)
                .collect(Collectors.toList());
    }

    // UPDATE: Update job status (e.g. ACCEPTED, REJECTED, COMPLETED, CANCELLED)
    @PutMapping("/{jobId}/status")
    @PreAuthorize("hasAnyRole('CLIENT', 'WORKER', 'ADMIN')")
    public ResponseEntity<?> updateJobStatus(@PathVariable UUID jobId, @RequestParam JobStatus status) {
        return jobRequestRepository.findById(jobId).map(job -> {
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
