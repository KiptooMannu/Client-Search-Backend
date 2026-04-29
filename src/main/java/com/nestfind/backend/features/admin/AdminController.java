package com.nestfind.backend.features.admin;

import com.nestfind.backend.features.auth.UserRepository;
import com.nestfind.backend.features.client.ClientProfile;
import com.nestfind.backend.features.client.ClientProfileDTO;
import com.nestfind.backend.features.client.ClientProfileRepository;
import com.nestfind.backend.features.worker.WorkerProfile;
import com.nestfind.backend.features.worker.WorkerProfileDTO;
import com.nestfind.backend.features.worker.WorkerProfileRepository;
import com.nestfind.backend.features.worker.WorkerStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@SuppressWarnings("null")
public class AdminController {

    private final WorkerProfileRepository workerProfileRepository;
    private final UserRepository userRepository;
    private final ClientProfileRepository clientProfileRepository; // <-- added

    // ==================== WORKER MANAGEMENT ====================

    @GetMapping("/workers/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public List<WorkerProfileDTO> getPendingWorkers() {
        return workerProfileRepository.findAllByStatusWithDetails(WorkerStatus.PENDING)
                .stream()
                .map(WorkerProfileDTO::from)
                .toList();
    }

    @GetMapping("/workers")
    @PreAuthorize("hasRole('ADMIN')")
    public List<WorkerProfileDTO> getAllWorkers() {
        return workerProfileRepository.findAllWithDetails()
                .stream()
                .map(WorkerProfileDTO::from)
                .toList();
    }

    @PutMapping("/workers/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> approveWorker(@PathVariable UUID id, @RequestParam UUID adminId) {
        WorkerProfile worker = workerProfileRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Worker not found"));

        if (worker.getPhoneNumber() == null || worker.getPhoneNumber().isBlank()) {
            return ResponseEntity.badRequest().body("Error: Worker must have a phone number before approval.");
        }
        if (worker.getSkills() == null || worker.getSkills().isEmpty()) {
            return ResponseEntity.badRequest().body("Error: Worker must have at least one skill listed.");
        }
        if (worker.getDocuments() == null || worker.getDocuments().isEmpty()) {
            return ResponseEntity.badRequest().body("Error: Worker must upload identity documents before approval.");
        }

        worker.setStatus(WorkerStatus.APPROVED);
        worker.setVisible(true);
        worker.setApprovedBy(adminId);
        worker.setApprovedAt(LocalDateTime.now());
        worker.setRejectionReason(null);

        workerProfileRepository.save(worker);
        return ResponseEntity.ok("Worker verified and is now live!");
    }

    @PutMapping("/workers/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> rejectWorker(@PathVariable UUID id, @RequestParam UUID adminId,
            @RequestParam String reason) {
        WorkerProfile worker = workerProfileRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Worker not found"));

        worker.setStatus(WorkerStatus.REJECTED);
        worker.setVisible(false);
        worker.setRejectedBy(adminId);
        worker.setRejectedAt(LocalDateTime.now());
        worker.setRejectionReason(reason);

        workerProfileRepository.save(worker);
        return ResponseEntity.ok("Worker rejected. Reason: " + reason);
    }

    @DeleteMapping("/workers/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteWorkerProfile(@PathVariable UUID id) {
        if (!workerProfileRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        workerProfileRepository.deleteById(id);
        return ResponseEntity.ok("Worker profile deleted.");
    }

    // ==================== CLIENT MANAGEMENT ====================

    @GetMapping("/clients")
    @PreAuthorize("hasRole('ADMIN')")
    public List<ClientProfileDTO> getAllClients() {
        return clientProfileRepository.findAll()
                .stream()
                .map(ClientProfileDTO::from)
                .collect(Collectors.toList());
    }

    @GetMapping("/clients/search")
    @PreAuthorize("hasRole('ADMIN')")
    public List<ClientProfileDTO> searchClients(@RequestParam(required = false) String keyword) {
        List<ClientProfile> clients;
        if (keyword != null && !keyword.isBlank()) {
            clients = clientProfileRepository.findByFullNameContainingIgnoreCaseOrUserEmailContainingIgnoreCase(keyword,
                    keyword);
        } else {
            clients = clientProfileRepository.findAll();
        }
        return clients.stream()
                .map(ClientProfileDTO::from)
                .collect(Collectors.toList());
    }

    @GetMapping("/clients/{profileId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ClientProfileDTO> getClientByProfileId(@PathVariable UUID profileId) {
        return clientProfileRepository.findById(profileId)
                .map(ClientProfileDTO::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/clients/{profileId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteClientProfile(@PathVariable UUID profileId) {
        if (!clientProfileRepository.existsById(profileId)) {
            return ResponseEntity.notFound().build();
        }
        clientProfileRepository.deleteById(profileId);
        return ResponseEntity.ok("Client profile deleted successfully.");
    }

    // ==================== USER MANAGEMENT ====================

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public List<java.util.Map<String, Object>> getAllUsers() {
        return userRepository.findAll().stream().map(u -> {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("email", u.getEmail());
            m.put("fullName", u.getFullName());
            m.put("role", u.getRole());
            m.put("createdAt", u.getCreatedAt());
            return m;
        }).toList();
    }

    @GetMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getUserById(@PathVariable UUID id) {
        return userRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteUser(@PathVariable UUID id) {
        if (!userRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        userRepository.deleteById(id);
        return ResponseEntity.ok("User deleted successfully.");
    }
}