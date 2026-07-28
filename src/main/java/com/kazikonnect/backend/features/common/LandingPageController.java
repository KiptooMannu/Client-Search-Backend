package com.kazikonnect.backend.features.common;

import com.kazikonnect.backend.features.auth.UserRepository;
import com.kazikonnect.backend.features.auth.UserRole;
import com.kazikonnect.backend.features.payment.EscrowPaymentRepository;
import com.kazikonnect.backend.features.payment.EscrowPaymentStatus;
import com.kazikonnect.backend.features.worker.JobRequestRepository;
import com.kazikonnect.backend.features.worker.JobStatus;
import com.kazikonnect.backend.features.worker.Review;
import com.kazikonnect.backend.features.worker.ReviewRepository;
import com.kazikonnect.backend.features.worker.WorkerProfile;
import com.kazikonnect.backend.features.worker.WorkerProfileRepository;
import com.kazikonnect.backend.features.worker.WorkerStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

/**
 * Unauthenticated, read-only figures for the marketing landing page.
 * <p>
 * Everything here is aggregate or already-public content: headline counts and
 * approved worker reviews. No per-user record is exposed, and reviewer identity
 * is reduced to a first name so a public page never leaks a full client name.
 */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class LandingPageController {

    /** Below this many reviews the testimonial rail stays hidden rather than looking bare. */
    private static final int MIN_TESTIMONIALS = 3;
    private static final int MAX_TESTIMONIALS = 6;
    /** Only genuinely positive reviews belong in a testimonial rail. */
    private static final int MIN_TESTIMONIAL_RATING = 4;

    private final UserRepository userRepository;
    private final WorkerProfileRepository workerProfileRepository;
    private final JobRequestRepository jobRequestRepository;
    private final EscrowPaymentRepository escrowPaymentRepository;
    private final ReviewRepository reviewRepository;

    public record LandingStats(
            long verifiedWorkers,
            long jobsCompleted,
            long registeredClients,
            double volumeProcessed,
            double averageRating,
            long totalReviews
    ) {}

    /**
     * @param reviewerName first name only — a public page should not carry full client names.
     * @param workerCategory the trade being praised, which is what a visitor is actually scanning for.
     */
    public record Testimonial(
            String reviewerName,
            int rating,
            String comment,
            String workerName,
            String workerCategory,
            String createdAt
    ) {}

    @GetMapping("/landing-stats")
    @Transactional(readOnly = true)
    public ResponseEntity<LandingStats> getLandingStats() {
        long verifiedWorkers = workerProfileRepository.countByStatus(WorkerStatus.APPROVED);

        long registeredClients = userRepository.findAll().stream()
                .filter(u -> u.getRole() == UserRole.CLIENT)
                .count();

        List<com.kazikonnect.backend.features.worker.JobRequest> allJobs = jobRequestRepository.findAll();
        long jobsCompleted = allJobs.stream()
                .filter(j -> j.getStatus() == JobStatus.COMPLETED || j.getStatus() == JobStatus.APPROVED)
                .count();

        // Only released escrow counts as processed — pending or failed money never moved.
        double volumeProcessed = escrowPaymentRepository.findAll().stream()
                .filter(p -> p.getStatus() == EscrowPaymentStatus.RELEASED)
                .mapToDouble(p -> p.getAmount() == null ? 0.0 : p.getAmount())
                .sum();

        List<Review> reviews = reviewRepository.findAll();
        double averageRating = reviews.stream()
                .filter(r -> r.getRating() != null)
                .mapToInt(Review::getRating)
                .average()
                .orElse(0.0);

        return ResponseEntity.ok(new LandingStats(
                verifiedWorkers,
                jobsCompleted,
                registeredClients,
                Math.round(volumeProcessed * 100.0) / 100.0,
                Math.round(averageRating * 10.0) / 10.0,
                reviews.size()
        ));
    }

    /**
     * Highest-rated recent reviews with an actual written comment.
     * <p>
     * Returns an empty list when fewer than {@link #MIN_TESTIMONIALS} qualify, so the
     * frontend hides the section entirely rather than showing a thin, unconvincing rail.
     */
    @GetMapping("/testimonials")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Testimonial>> getTestimonials() {
        List<Testimonial> testimonials = reviewRepository.findAll().stream()
                .filter(r -> r.getRating() != null && r.getRating() >= MIN_TESTIMONIAL_RATING)
                .filter(r -> r.getComment() != null && !r.getComment().isBlank())
                .sorted(Comparator
                        .comparing(Review::getRating, Comparator.reverseOrder())
                        .thenComparing(Review::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(MAX_TESTIMONIALS)
                .map(this::toTestimonial)
                .toList();

        if (testimonials.size() < MIN_TESTIMONIALS) {
            return ResponseEntity.ok(List.of());
        }
        return ResponseEntity.ok(testimonials);
    }

    private Testimonial toTestimonial(Review review) {
        WorkerProfile worker = review.getWorker();
        return new Testimonial(
                firstNameOf(review),
                review.getRating(),
                review.getComment().trim(),
                worker != null && worker.getFullName() != null ? worker.getFullName() : "A Kazi Konnect pro",
                worker != null && worker.getCategory() != null ? worker.getCategory() : "Verified professional",
                review.getCreatedAt() != null ? review.getCreatedAt().toString() : null
        );
    }

    /** Reduces the reviewer to a first name, falling back to "Verified client". */
    private String firstNameOf(Review review) {
        if (review.getClient() == null) {
            return "Verified client";
        }
        String name = review.getClient().getFullName();
        if (name == null || name.isBlank()) {
            name = review.getClient().getFirstName();
        }
        if (name == null || name.isBlank()) {
            return "Verified client";
        }
        return name.trim().split("\\s+")[0];
    }
}
