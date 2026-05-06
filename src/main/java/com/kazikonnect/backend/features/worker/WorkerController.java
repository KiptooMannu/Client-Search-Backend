package com.kazikonnect.backend.features.worker;

import com.kazikonnect.backend.features.auth.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import java.security.Principal;

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
    private final com.kazikonnect.backend.core.services.CloudinaryService cloudinaryService;

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
            
            // Removed default placeholder document seeding to allow for real worker uploads
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

        // New profiles start in DRAFT status
        if (profileToSave.getStatus() == null) {
            profileToSave.setStatus(WorkerStatus.DRAFT);
        }
        profileToSave.setVisible(false);

        // Handle Work History
        if (updates.workHistory() != null) {
            workHistoryRepository.deleteByWorkerId(profileToSave.getId());
            workHistoryRepository.flush();
            profileToSave.getWorkHistory().clear();
            updates.workHistory().forEach(dto -> {
                WorkHistory wh = WorkHistory.builder()
                    .worker(profileToSave)
                    .company(dto.company())
                    .role(dto.role())
                    .period(dto.period())
                    .description(dto.description())
                    .build();
                workHistoryRepository.save(wh);
                profileToSave.getWorkHistory().add(wh);
            });
        }

        // Handle Certifications
        if (updates.certifications() != null) {
            certificationRepository.deleteByWorkerId(profileToSave.getId());
            certificationRepository.flush();
            profileToSave.getCertifications().clear();
            updates.certifications().forEach(dto -> {
                Certification cert = Certification.builder()
                    .worker(profileToSave)
                    .name(dto.name())
                    .issuer(dto.issuer())
                    .year(dto.year())
                    .build();
                certificationRepository.save(cert);
                profileToSave.getCertifications().add(cert);
            });
        }

        WorkerProfile saved = workerProfileRepository.saveAndFlush(profileToSave);

        return ResponseEntity.ok(new ProfileUpdateResponse(
            "Profile updated successfully", 
            WorkerProfileDTO.from(saved)
        ));
    }


    // READ: Get a single worker profile by userId
    @GetMapping("/profile/{userId}")
    @PreAuthorize("hasRole('WORKER') or hasRole('ADMIN')")
    public ResponseEntity<?> getProfile(@PathVariable UUID userId, Principal principal) {
        var actor = userRepository.findByUsername(principal.getName()).orElse(null);
        if (actor == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }
        if (actor.getRole() != com.kazikonnect.backend.features.auth.UserRole.ADMIN && !actor.getId().equals(userId)) {
            return ResponseEntity.status(403).body("Forbidden: cannot access another worker profile.");
        }
        return workerProfileRepository.findByUserId(userId)
                .map(p -> ResponseEntity.ok(WorkerProfileDTO.from(p)))
                .orElse(ResponseEntity.notFound().build());
    }

    // UPDATE: Worker edits their profile (resets to PENDING for re-verification)
    @PutMapping("/profile/{profileId}")
    @PreAuthorize("hasRole('WORKER')")
    public ResponseEntity<?> updateProfile(@PathVariable UUID profileId, @RequestBody WorkerProfileUpdateRequest updates, Principal principal) {
        System.out.println("Processing UPDATE request for profileId: " + profileId);
        return workerProfileRepository.findById(profileId).map(existing -> {
            var actor = userRepository.findByUsername(principal.getName()).orElse(null);
            if (actor == null) {
                return ResponseEntity.status(401).body("Unauthorized");
            }
            if (existing.getUser() == null || !existing.getUser().getId().equals(actor.getId())) {
                return ResponseEntity.status(403).body("Forbidden: cannot edit another worker profile.");
            }
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
                for (CertificationCreateDTO dto : updates.certifications()) {
                    Certification cert = Certification.builder()
                        .worker(existing)
                        .name(dto.name())
                        .issuer(dto.issuer())
                        .year(dto.year())
                        .build();
                    existing.getCertifications().add(cert);
                }
            }

            // If profile was already approved, editing it resets it to PENDING for re-review
            // If it was DRAFT or REJECTED, it stays in that state until explicitly submitted
            if (existing.getStatus() == WorkerStatus.APPROVED) {
                existing.setStatus(WorkerStatus.PENDING);
                existing.setVisible(false);
            }
            return ResponseEntity.ok(WorkerProfileDTO.from(workerProfileRepository.save(existing)));
        }).orElse(ResponseEntity.notFound().build());
    }

    // UPDATE: Upload profile picture
    @PostMapping("/{profileId}/profile-picture")
    @PreAuthorize("hasRole('WORKER')")
    public ResponseEntity<?> uploadProfilePicture(
            @PathVariable UUID profileId,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            Principal principal) {
        
        return workerProfileRepository.findById(profileId).map(existing -> {
            var actor = userRepository.findByUsername(principal.getName()).orElse(null);
            if (actor == null) {
                return ResponseEntity.status(401).body("Unauthorized");
            }
            if (existing.getUser() == null || !existing.getUser().getId().equals(actor.getId())) {
                return ResponseEntity.status(403).body("Forbidden: cannot edit another worker profile.");
            }
            try {
                java.util.Map<?, ?> uploadResult = cloudinaryService.upload(file, "Kazi Konnect/profiles/" + profileId);
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
    public ResponseEntity<?> submitForVerification(@PathVariable UUID profileId, Principal principal) {
        return submitForVerificationInternal(profileId, principal);
    }

    // Backward-compatible endpoint used by frontend routing style
    @PutMapping("/profile/{profileId}/submit")
    @PreAuthorize("hasRole('WORKER')")
    public ResponseEntity<?> submitForVerificationFromProfileRoute(@PathVariable UUID profileId, Principal principal) {
        return submitForVerificationInternal(profileId, principal);
    }

    private ResponseEntity<?> submitForVerificationInternal(UUID profileId, Principal principal) {
        return workerProfileRepository.findById(profileId).map(existing -> {
            var actor = userRepository.findByUsername(principal.getName()).orElse(null);
            if (actor == null) {
                return ResponseEntity.status(401).body("Unauthorized");
            }
            if (existing.getUser() == null || !existing.getUser().getId().equals(actor.getId())) {
                return ResponseEntity.status(403).body("Forbidden: cannot submit another worker profile.");
            }
            existing.setStatus(WorkerStatus.PENDING);
            existing.setVisible(false);
            return ResponseEntity.ok(WorkerProfileDTO.from(workerProfileRepository.save(existing)));
        }).orElse(ResponseEntity.notFound().build());
    }

    // DELETE: Worker deletes their own profile
    @DeleteMapping("/profile/{profileId}")
    @PreAuthorize("hasRole('WORKER') or hasRole('ADMIN')")
    public ResponseEntity<?> deleteProfile(@PathVariable UUID profileId, Principal principal) {
        var actor = userRepository.findByUsername(principal.getName()).orElse(null);
        if (actor == null) {
            return ResponseEntity.status(401).body("Unauthorized");
        }
        return workerProfileRepository.findById(profileId).map(existing -> {
            boolean admin = actor.getRole() == com.kazikonnect.backend.features.auth.UserRole.ADMIN;
            boolean owner = existing.getUser() != null && existing.getUser().getId().equals(actor.getId());
            if (!admin && !owner) {
                return ResponseEntity.status(403).body("Forbidden: cannot delete another worker profile.");
            }
            workerProfileRepository.deleteById(profileId);
            return ResponseEntity.ok("Profile deleted successfully.");
        }).orElse(ResponseEntity.notFound().build());
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
    java.util.List<CertificationCreateDTO> certifications
) {}
