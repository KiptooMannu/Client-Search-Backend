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
    public ResponseEntity<?> createProfile(@RequestBody WorkerProfile updates, @RequestParam String email) {
        var existingOpt = workerProfileRepository.findByUserEmail(email);
        WorkerProfile profileToSave;

        if (existingOpt.isPresent()) {
            profileToSave = existingOpt.get();
            // Merge basic fields
            if (updates.getFullName() != null) profileToSave.setFullName(updates.getFullName());
            if (updates.getPhoneNumber() != null) profileToSave.setPhoneNumber(updates.getPhoneNumber());
            if (updates.getBio() != null) profileToSave.setBio(updates.getBio());
            if (updates.getLocation() != null) profileToSave.setLocation(updates.getLocation());
            if (updates.getExperienceYears() != null) profileToSave.setExperienceYears(updates.getExperienceYears());
            if (updates.getHourlyRate() != null) profileToSave.setHourlyRate(updates.getHourlyRate());
            if (updates.getCategory() != null) profileToSave.setCategory(updates.getCategory());
            if (updates.getProfilePictureUrl() != null) profileToSave.setProfilePictureUrl(updates.getProfilePictureUrl());
            if (updates.getAvailabilityDetails() != null) profileToSave.setAvailabilityDetails(updates.getAvailabilityDetails());
        } else {
            var user = userRepository.findByEmail(email).orElse(null);
            if (user == null) {
                return ResponseEntity.badRequest().body("Error: User not found for email: " + email);
            }
            profileToSave = updates;
            profileToSave.setUser(user);
            
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
        if (updates.getSkills() != null) {
            java.util.Set<Skill> managedSkills = updates.getSkills().stream()
                .map(s -> skillRepository.findByName(s.getName())
                    .orElseGet(() -> skillRepository.save(Skill.builder().name(s.getName()).build())))
                .collect(java.util.stream.Collectors.toSet());
            profileToSave.setSkills(managedSkills);
        }

        if (updates.getPreferredLocations() != null) {
            profileToSave.setPreferredLocations(updates.getPreferredLocations());
        }

        // Reset status for re-verification
        profileToSave.setStatus(WorkerStatus.PENDING);
        profileToSave.setVisible(false);

        // Save profile first
        WorkerProfile saved = workerProfileRepository.save(profileToSave);

        // Handle Work History (Clear old, add new with back-reference)
        if (updates.getWorkHistory() != null && updates.getWorkHistory() != saved.getWorkHistory()) {
            workHistoryRepository.deleteByWorkerId(saved.getId());
            saved.getWorkHistory().clear();
            updates.getWorkHistory().forEach(h -> {
                h.setWorker(saved);
                WorkHistory savedH = workHistoryRepository.save(h);
                saved.getWorkHistory().add(savedH);
            });
        }

        // Handle Certifications (Clear old, add new with back-reference)
        if (updates.getCertifications() != null && updates.getCertifications() != saved.getCertifications()) {
            certificationRepository.deleteByWorkerId(saved.getId());
            saved.getCertifications().clear();
            updates.getCertifications().forEach(c -> {
                c.setWorker(saved);
                Certification savedC = certificationRepository.save(c);
                saved.getCertifications().add(savedC);
            });
        }

        // Handle Documents (Clear old, add new with back-reference)
        if (updates.getDocuments() != null && updates.getDocuments() != saved.getDocuments()) {
            documentRepository.deleteByWorkerId(saved.getId());
            saved.getDocuments().clear();
            updates.getDocuments().forEach(d -> {
                d.setWorker(saved);
                Document savedD = documentRepository.save(d);
                saved.getDocuments().add(savedD);
            });
        }

        // Flush changes to database
        workerProfileRepository.flush();

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
                    .map(s -> skillRepository.findByName(s.name())
                        .orElseGet(() -> skillRepository.save(Skill.builder().name(s.name()).build())))
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
    java.util.Set<SkillRequest> skills,
    String profilePictureUrl,
    Availability availabilityDetails,
    java.util.List<WorkHistoryDTO> workHistory,
    java.util.List<CertificationDTO> certifications
) {}

record SkillRequest(String name) {}
