package com.kazikonnect.backend.features.worker;

import com.kazikonnect.backend.features.auth.User;
import com.kazikonnect.backend.features.auth.UserRepository;
import com.kazikonnect.backend.features.common.Notification;
import com.kazikonnect.backend.features.common.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
@SuppressWarnings("null")
public class ReviewController {

    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final WorkerProfileRepository workerProfileRepository;
    private final JobRequestRepository jobRequestRepository;
    private final NotificationRepository notificationRepository;

    // CREATE: Client leaves a review for a worker
    @PostMapping
    @PreAuthorize("hasRole('CLIENT')")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> createReview(
            @RequestParam UUID clientId,
            @RequestParam UUID workerProfileId,
            @RequestParam(required = false) UUID jobId,
            @RequestBody Review review) {
        
        User client = userRepository.findById(clientId).orElse(null);
        WorkerProfile worker = workerProfileRepository.findById(workerProfileId).orElse(null);

        if (client == null || worker == null) {
            return ResponseEntity.badRequest().body("Client or Worker not found.");
        }

        // Ensure review is properly linked
        review.setClient(client);
        review.setWorker(worker);

        // Link to Job if jobId is provided
        if (jobId != null) {
            if (reviewRepository.findByJobRequestId(jobId).isPresent()) {
                return ResponseEntity.badRequest().body("A review already exists for this job.");
            }
            jobRequestRepository.findById(jobId).ifPresent(job -> {
                review.setJobRequest(job);
                // Finalize the job lifecycle: COMPLETED
                job.setStatus(JobStatus.COMPLETED);
                jobRequestRepository.save(job);
            });
        }

        Review saved = reviewRepository.save(review);

        // Notify Worker
        Notification notification = Notification.builder()
                .user(worker.getUser())
                .title("New Review Received!")
                .message("A client left you a " + review.getRating() + "-star review and the project has been finalized.")
                .type("SUCCESS")
                .build();
        notificationRepository.save(notification);

        return ResponseEntity.ok(ReviewDTO.from(saved));
    }

    // READ: Get all reviews for a worker
    @GetMapping("/worker/{workerProfileId}")
    public List<ReviewDTO> getWorkerReviews(@PathVariable UUID workerProfileId) {
        return reviewRepository.findAllByWorkerId(workerProfileId).stream()
                .map(ReviewDTO::from)
                .collect(Collectors.toList());
    }

    // READ: Get all reviews left by a client
    @GetMapping("/client/{clientId}")
    @PreAuthorize("hasRole('CLIENT') or hasRole('ADMIN')")
    public List<ReviewDTO> getClientReviews(@PathVariable UUID clientId) {
        return reviewRepository.findAllByClientId(clientId).stream()
                .map(ReviewDTO::from)
                .collect(Collectors.toList());
    }

    // UPDATE: Client edits their review
    @PutMapping("/{reviewId}")
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<?> updateReview(@PathVariable UUID reviewId, @RequestBody Review updates) {
        return reviewRepository.findById(reviewId).map(existing -> {
            if (updates.getRating() != null) existing.setRating(updates.getRating());
            if (updates.getComment() != null) existing.setComment(updates.getComment());
            return ResponseEntity.ok(ReviewDTO.from(reviewRepository.save(existing)));
        }).orElse(ResponseEntity.notFound().build());
    }

    // DELETE: Delete a review
    @DeleteMapping("/{reviewId}")
    @PreAuthorize("hasRole('CLIENT') or hasRole('ADMIN')")
    public ResponseEntity<?> deleteReview(@PathVariable UUID reviewId) {
        if (!reviewRepository.existsById(reviewId)) {
            return ResponseEntity.notFound().build();
        }
        reviewRepository.deleteById(reviewId);
        return ResponseEntity.ok("Review deleted successfully.");
    }
}
