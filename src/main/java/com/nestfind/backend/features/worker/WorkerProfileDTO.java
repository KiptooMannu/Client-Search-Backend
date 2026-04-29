package com.nestfind.backend.features.worker;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record WorkerProfileDTO(
    UUID id,
    String username,
    String email,
    String fullName,
    String phoneNumber,
    Integer experienceYears,
    String location,
    String bio,
    String status,
    Double hourlyRate,
    String category,
    String profilePictureUrl,
    boolean isOnline,
    String rejectionReason,
    LocalDateTime createdAt,
    Set<String> skills,
    Set<String> preferredLocations,
    List<WorkHistoryDTO> workHistory,
    List<CertificationDTO> certifications,
    List<DocumentDTO> documents,
    Availability availabilityDetails
) {
    public static WorkerProfileDTO from(WorkerProfile p) {
        Set<String> skillNames = p.getSkills() == null ? Set.of() :
            p.getSkills().stream().map(Skill::getName).collect(Collectors.toSet());

        List<WorkHistoryDTO> history = p.getWorkHistory() == null ? List.of() :
            p.getWorkHistory().stream().map(WorkHistoryDTO::from).collect(Collectors.toList());

        List<CertificationDTO> certs = p.getCertifications() == null ? List.of() :
            p.getCertifications().stream().map(CertificationDTO::from).collect(Collectors.toList());

        List<DocumentDTO> docs = p.getDocuments() == null ? List.of() :
            p.getDocuments().stream().map(DocumentDTO::from).collect(Collectors.toList());

        return new WorkerProfileDTO(
            p.getId(),
            p.getUser() != null ? p.getUser().getUsername() : null,
            p.getUser() != null ? p.getUser().getEmail() : null,
            p.getFullName(),
            p.getPhoneNumber(),
            p.getExperienceYears(),
            p.getLocation(),
            p.getBio(),
            p.getStatus().name(),
            p.getHourlyRate(),
            p.getCategory(),
            p.getProfilePictureUrl(),
            p.isOnline(),
            p.getRejectionReason(),
            p.getCreatedAt(),
            skillNames,
            p.getPreferredLocations(),
            history,
            certs,
            docs,
            p.getAvailabilityDetails()
        );
    }
}
