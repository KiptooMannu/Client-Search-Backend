package com.kazikonnect.backend.features.worker;

import com.kazikonnect.backend.features.auth.User;
import com.kazikonnect.backend.features.auth.UserRepository;
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

    // CREATE: Client leaves a review for a worker
    @PostMapping
    @PreAuthorize("hasRole('CLIENT')")
    public ResponseEntity<?> createReview(
            @RequestParam UUID clientId,
            @RequestParam UUID workerProfileId,
            @RequestBody Review review) {
        
        User client = userRepository.findById(clientId).orElse(null);
        WorkerProfile worker = workerProfileRepository.findById(workerProfileId).orElse(null);

        if (client == null || worker == null) {
            return ResponseEntity.badRequest().body("Client or Worker not found.");
        }

        review.setClient(client);
        review.setWorker(worker);

        Review saved = reviewRepository.save(review);
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
