package com.kazikonnect.backend.features.worker;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/marketplace")
@RequiredArgsConstructor
@SuppressWarnings("null")
public class MarketplaceController {

    private final WorkerProfileRepository workerProfileRepository;
    private final SkillRepository skillRepository;

    // READ: Get all available skills
    @GetMapping("/skills")
    public List<Skill> listSkills() {
        return skillRepository.findAll();
    }

    @GetMapping("/locations")
    public List<String> listLocations() {
        return workerProfileRepository.findDistinctLocations();
    }

    // READ: Advanced Marketplace Search
    @GetMapping("/search")
    public List<WorkerProfileDTO> search(
            @RequestParam(required = false) String skill,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) Integer minExp) {
        
        return workerProfileRepository.advancedSearch(WorkerStatus.APPROVED, skill, location, minExp)
                .stream()
                .map(WorkerProfileDTO::from)
                .collect(Collectors.toList());
    }

    // READ: Get a single worker's public profile (for client to view)
    @GetMapping("/workers/{profileId}")
    public WorkerProfileDTO getWorkerProfile(@PathVariable UUID profileId) {
        WorkerProfile profile = workerProfileRepository.findById(profileId)
                .orElseThrow(() -> new RuntimeException("Worker not found"));
        if (profile.getStatus() != WorkerStatus.APPROVED || !profile.isVisible()) {
            throw new RuntimeException("Worker not available in marketplace");
        }
        return WorkerProfileDTO.from(profile);
    }
}
