package com.kazikonnect.backend.features.worker;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record MarketplaceWorkerDTO(
    UUID id,
    UUID userId,
    String username,
    String email,
    String fullName,
    String profilePictureUrl,
    String category,
    String location,
    Double hourlyRate,
    Integer experienceYears,
    boolean isOnline,
    String bio,
    Set<String> skills,
    Set<String> preferredLocations,
    double averageRating,
    int reviewCount,
    String status
) {
    public static MarketplaceWorkerDTO from(WorkerProfile profile) {
        return new MarketplaceWorkerDTO(
            profile.getId(),
            profile.getUser() != null ? profile.getUser().getId() : null,
            profile.getUser() != null ? profile.getUser().getUsername() : null,
            profile.getUser() != null ? profile.getUser().getEmail() : null,
            profile.getFullName(),
            profile.getProfilePictureUrl(),
            profile.getCategory(),
            profile.getLocation(),
            profile.getHourlyRate(),
            profile.getExperienceYears(),
            profile.isOnline(),
            profile.getBio(),
            profile.getSkills() == null ? Set.of() : profile.getSkills().stream().map(skill -> skill.getName()).collect(Collectors.toSet()),
            profile.getPreferredLocations() == null ? Set.of() : profile.getPreferredLocations(),
            profile.getReviews() == null || profile.getReviews().isEmpty()
                ? 0.0
                : profile.getReviews().stream().mapToInt(review -> review.getRating()).average().orElse(0.0),
            profile.getReviews() == null ? 0 : profile.getReviews().size(),
            profile.getStatus() != null ? profile.getStatus().name() : null
        );
    }
}
