package com.nestfind.backend.features.worker;

import com.nestfind.backend.features.auth.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/workers")
@RequiredArgsConstructor
@SuppressWarnings("null")
public class WorkerController {

    private final WorkerProfileRepository workerProfileRepository;
    private final UserRepository userRepository;
    private final SkillRepository skillRepository;
    private final WorkHistoryRepository workHistoryRepository;
    private final CertificationRepository certificationRepository;
    private final DocumentRepository documentRepository;
    private final com.nestfind.backend.core.services.CloudinaryService cloudinaryService;

    // CREATE: Submit a new worker profile (updates existing if already present)
    @PostMapping("/profile")
    @PreAuthorize("hasRole('WORKER')")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<?> createProfile(@RequestBody WorkerProfileUpdateRequest updates, @RequestParam String email) {
        System.out.println("Processing CREATE request for email: " + email);
        var existingOpt = workerProfileRepository.findByUserEmail(email);
        WorkerProfile profileToSave;

        if (existingOpt.isPresent()) {
            profileToSave = existingOpt.get();
            // Merge basic fields
            if (updates.fullName() != null) profileToSave.setFullName(updates.fullName());
            if (updates.phoneNumber() != null) profileToSave.setPhoneNumber(updates.phoneNumber());
            if (updates.bio() != null) profileToSave.setBio(updates.bio());
            if (updates.location() != null) profileToSave.setLocation(updates.location());
            if (updates.experienceYears() != null) profileToSave.setExperienceYears(updates.experienceYears());
            if (updates.hourlyRate() != null) profileToSave.setHourlyRate(updates.hourlyRate());
            if (updates.category() != null) profileToSave.setCategory(updates.category());
            if (updates.profilePictureUrl() != null) profileToSave.setProfilePictureUrl(updates.profilePictureUrl());
            if (updates.availabilityDetails() != null) profileToSave.setAvailabilityDetails(updates.availabilityDetails());
        } else {
            var user = userRepository.findByEmail(email).orElse(null);
            if (user == null) {
                return ResponseEntity.badRequest().body("Error: User not found for email: " + email);
            }
            profileToSave = new WorkerProfile();
            profileToSave.setUser(user);
            profileToSave.setFullName(updates.fullName());
            profileToSave.setPhoneNumber(updates.phoneNumber());
            profileToSave.setBio(updates.bio());
            profileToSave.setLocation(updates.location());
            profileToSave.setExperienceYears(updates.experienceYears());
            profileToSave.setHourlyRate(updates.hourlyRate());
            profileToSave.setCategory(updates.category());
            profileToSave.setProfilePictureUrl(updates.profilePictureUrl());
            profileToSave.setAvailabilityDetails(updates.availabilityDetails());
            
            // Seed default placeholder documents for new API-created profiles
            Document idCard = new Document();
            idCard.setWorker(profileToSave);
            idCard.setName("National_ID");
            idCard.setType("ID_CARD");
            idCard.setDocumentUrl("https://res.cloudinary.com/demo/image/upload/sample_id.jpg");
            profileToSave.getDocuments().add(idCard);

            Document cert = new Document();
            cert.setWorker(profileToSave);
            cert.setName("Technical_Certificate");
            cert.setType("CERTIFICATE");
            cert.setDocumentUrl("https://res.cloudinary.com/demo/image/upload/sample_cert.jpg");
            profileToSave.getDocuments().add(cert);
        }

        // Handle Skills
        if (updates.skills() != null) {
            java.util.Set<Skill> managedSkills = updates.skills().stream()
                .map(name -> skillRepository.findByName(name)
                    .orElseGet(() -> skillRepository.save(Skill.builder().name(name).build())))
                .collect(java.util.stream.Collectors.toSet());
            profileToSave.setSkills(managedSkills);
        }

        if (updates.preferredLocations() != null) {
            profileToSave.setPreferredLocations(updates.preferredLocations());
        }

        // Reset status for re-verification
        profileToSave.setStatus(WorkerStatus.PENDING);
        profileToSave.setVisible(false);

        // Save profile first
        WorkerProfile saved = workerProfileRepository.save(profileToSave);

        // Handle Work History
        if (updates.workHistory() != null) {
            workHistoryRepository.deleteByWorkerId(saved.getId());
            saved.getWorkHistory().clear();
            updates.workHistory().forEach(dto -> {
                WorkHistory wh = WorkHistory.builder()
                    .worker(saved)
                    .company(dto.company())
                    .role(dto.role())
                    .period(dto.period())
                    .description(dto.description())
                    .build();
                workHistoryRepository.save(wh);
                saved.getWorkHistory().add(wh);
            });
        }

        // Handle Certifications
        if (updates.certifications() != null) {
            certificationRepository.deleteByWorkerId(saved.getId());
            saved.getCertifications().clear();
            updates.certifications().forEach(dto -> {
                Certification cert = Certification.builder()
                    .worker(saved)
                    .name(dto.name())
                    .issuer(dto.issuer())
                    .year(dto.year())
                    .build();
                certificationRepository.save(cert);
                saved.getCertifications().add(cert);
            });
        }

        return ResponseEntity.ok(new ProfileUpdateResponse(
            "Profile updated successfully", 
            WorkerProfileDTO.from(saved)
        ));
    }


    // READ: Get a single worker profile by userId
    @GetMapping("/profile/{userId}")
    @PreAuthorize("hasRole('WORKER') or hasRole('ADMIN')")
    public ResponseEntity<?> getProfile(@PathVariable UUID userId) {
        return workerProfileRepository.findByUserId(userId)
                .map(p -> ResponseEntity.ok(WorkerProfileDTO.from(p)))
                .orElse(ResponseEntity.notFound().build());
    }

    // UPDATE: Worker edits their profile (resets to PENDING for re-verification)
    @PutMapping("/profile/{profileId}")
    @PreAuthorize("hasRole('WORKER')")
    public ResponseEntity<?> updateProfile(@PathVariable UUID profileId, @RequestBody WorkerProfileUpdateRequest updates) {
        System.out.println("Processing UPDATE request for profileId: " + profileId);
        return workerProfileRepository.findById(profileId).map(existing -> {
            if (updates.fullName() != null) existing.setFullName(updates.fullName());
            if (updates.phoneNumber() != null) existing.setPhoneNumber(updates.phoneNumber());
            if (updates.bio() != null) existing.setBio(updates.bio());
            if (updates.location() != null) existing.setLocation(updates.location());
            if (updates.experienceYears() != null) existing.setExperienceYears(updates.experienceYears());
            if (updates.hourlyRate() != null) existing.setHourlyRate(updates.hourlyRate());
            if (updates.category() != null) existing.setCategory(updates.category());
            if (updates.profilePictureUrl() != null) existing.setProfilePictureUrl(updates.profilePictureUrl());
            if (updates.availabilityDetails() != null) existing.setAvailabilityDetails(updates.availabilityDetails());
            
            if (updates.skills() != null) {
                java.util.Set<Skill> managedSkills = updates.skills().stream()
                    .map(skillName -> skillRepository.findByName(skillName)
                        .orElseGet(() -> skillRepository.save(Skill.builder().name(skillName).build())))
                    .collect(java.util.stream.Collectors.toSet());
                existing.setSkills(managedSkills);
            }

            if (updates.preferredLocations() != null) {
                existing.setPreferredLocations(updates.preferredLocations());
            }

            if (updates.workHistory() != null) {
                existing.getWorkHistory().clear();
                for (WorkHistoryDTO dto : updates.workHistory()) {
                    WorkHistory wh = WorkHistory.builder()
                        .worker(existing)
                        .company(dto.company())
                        .role(dto.role())
                        .period(dto.period())
                        .description(dto.description())
                        .build();
                    existing.getWorkHistory().add(wh);
                }
            }

            if (updates.certifications() != null) {
                existing.getCertifications().clear();
                for (CertificationDTO dto : updates.certifications()) {
                    Certification cert = Certification.builder()
                        .worker(existing)
                        .name(dto.name())
                        .issuer(dto.issuer())
                        .year(dto.year())
                        .build();
                    existing.getCertifications().add(cert);
                }
            }

            // Editing a profile resets it to PENDING for re-review
            existing.setStatus(WorkerStatus.PENDING);
            existing.setVisible(false);
            return ResponseEntity.ok(WorkerProfileDTO.from(workerProfileRepository.save(existing)));
        }).orElse(ResponseEntity.notFound().build());
    }

    // UPDATE: Upload profile picture
    @PostMapping("/{profileId}/profile-picture")
    @PreAuthorize("hasRole('WORKER')")
    public ResponseEntity<?> uploadProfilePicture(
            @PathVariable UUID profileId,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
        
        return workerProfileRepository.findById(profileId).map(existing -> {
            try {
                java.util.Map<?, ?> uploadResult = cloudinaryService.upload(file, "nestfind/profiles/" + profileId);
                String fileUrl = (String) uploadResult.get("secure_url");
                existing.setProfilePictureUrl(fileUrl);
                return ResponseEntity.ok(WorkerProfileDTO.from(workerProfileRepository.save(existing)));
            } catch (java.io.IOException e) {
                return ResponseEntity.internalServerError().body("Failed to upload profile picture: " + e.getMessage());
            }
        }).orElse(ResponseEntity.notFound().build());
    }

    // UPDATE: Explicitly submit for verification
    @PutMapping("/{profileId}/submit")
    @PreAuthorize("hasRole('WORKER')")
    public ResponseEntity<?> submitForVerification(@PathVariable UUID profileId) {
        return workerProfileRepository.findById(profileId).map(existing -> {
            existing.setStatus(WorkerStatus.PENDING);
            existing.setVisible(false);
            return ResponseEntity.ok(WorkerProfileDTO.from(workerProfileRepository.save(existing)));
        }).orElse(ResponseEntity.notFound().build());
    }

    // DELETE: Worker deletes their own profile
    @DeleteMapping("/profile/{profileId}")
    @PreAuthorize("hasRole('WORKER') or hasRole('ADMIN')")
    public ResponseEntity<?> deleteProfile(@PathVariable UUID profileId) {
        if (!workerProfileRepository.existsById(profileId)) {
            return ResponseEntity.notFound().build();
        }
        workerProfileRepository.deleteById(profileId);
        return ResponseEntity.ok("Profile deleted successfully.");
    }
}

record WorkerProfileUpdateRequest(
    String fullName,
    String phoneNumber,
    Integer experienceYears,
    Double hourlyRate,
    String category,
    String location,
    String bio,
    java.util.Set<String> preferredLocations,
    java.util.Set<String> skills,
    String profilePictureUrl,
    Availability availabilityDetails,
    java.util.List<WorkHistoryDTO> workHistory,
    java.util.List<CertificationDTO> certifications
) {}
