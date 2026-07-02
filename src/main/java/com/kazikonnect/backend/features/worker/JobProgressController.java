package com.kazikonnect.backend.features.worker;

import com.kazikonnect.backend.features.auth.User;
import com.kazikonnect.backend.features.auth.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
@Slf4j
public class JobProgressController {

    private final JobRequestRepository jobRequestRepository;
    private final JobProgressRepository jobProgressRepository;
    private final UserRepository userRepository;

    @GetMapping("/{jobId}/progress")
    @PreAuthorize("hasAuthority('Client') or hasAuthority('Worker') or hasAuthority('Admin')")
    public ResponseEntity<?> getJobProgress(@PathVariable UUID jobId, Principal principal) {
        return jobRequestRepository.findById(Objects.requireNonNull(jobId))
                .map(job -> {
                    User actor = userRepository.findByUsername(Objects.requireNonNull(principal).getName()).orElse(null);
                    if (actor == null) {
                        return ResponseEntity.status(401).body("Unauthorized.");
                    }
                    boolean admin = actor.getRole() == com.kazikonnect.backend.features.auth.UserRole.ADMIN;
                    boolean clientOwner = job.getClient() != null && actor.getId().equals(job.getClient().getId());
                    boolean workerOwner = job.getWorker() != null && job.getWorker().getUser() != null && actor.getId().equals(job.getWorker().getUser().getId());
                    if (!admin && !clientOwner && !workerOwner) {
                        return ResponseEntity.status(403).body("Forbidden.");
                    }

                    List<JobProgressDTO> progress = jobProgressRepository.findByJobRequestIdOrderByCreatedAtAsc(jobId)
                            .stream()
                            .map(JobProgressDTO::from)
                            .collect(Collectors.toList());
                    return ResponseEntity.ok(progress);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{jobId}/progress")
    @PreAuthorize("hasAuthority('Worker')")
    public ResponseEntity<?> submitJobProgress(
            @PathVariable UUID jobId,
            @RequestParam String description,
            @RequestParam(required = false) String attachmentUrl,
            Principal principal) {
        return jobRequestRepository.findById(Objects.requireNonNull(jobId))
                .map(job -> {
                    User actor = userRepository.findByUsername(Objects.requireNonNull(principal).getName()).orElse(null);
                    if (actor == null) {
                        return ResponseEntity.status(401).body("Unauthorized.");
                    }
                    if (job.getWorker() == null || job.getWorker().getUser() == null || !actor.getId().equals(job.getWorker().getUser().getId())) {
                        return ResponseEntity.status(403).body("Forbidden: Only the assigned worker can submit progress.");
                    }

                    JobProgress progress = Objects.requireNonNull(JobProgress.builder()
                            .jobRequest(job)
                            .description(Objects.requireNonNull(description))
                            .attachmentUrl(attachmentUrl)
                            .build());
                    JobProgress savedProgress = Objects.requireNonNull(jobProgressRepository.save(progress));
                    return ResponseEntity.ok(JobProgressDTO.from(savedProgress));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}
