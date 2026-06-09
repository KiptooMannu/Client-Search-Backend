package com.kazikonnect.backend.features.worker;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;


@RestController
@RequestMapping("/api/marketplace")
@RequiredArgsConstructor
@org.springframework.transaction.annotation.Transactional(readOnly = true)
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

    @GetMapping("/search")
    public org.springframework.data.domain.Page<MarketplaceWorkerDTO> search(
            @RequestParam(required = false) String skill,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) Integer minExp,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        return workerProfileRepository.advancedSearchPaged(WorkerStatus.APPROVED, skill, location, minExp, pageable)
                .map(MarketplaceWorkerDTO::from);
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
