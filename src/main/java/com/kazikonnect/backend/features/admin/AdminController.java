package com.kazikonnect.backend.features.admin;

import com.kazikonnect.backend.features.auth.UserRepository;
import com.kazikonnect.backend.features.auth.AuthRepository;
import com.kazikonnect.backend.features.client.ClientProfile;
import com.kazikonnect.backend.features.client.ClientProfileDTO;
import com.kazikonnect.backend.features.client.ClientProfileRepository;
import com.kazikonnect.backend.features.worker.WorkerProfile;
import com.kazikonnect.backend.features.worker.WorkerProfileDTO;
import com.kazikonnect.backend.features.worker.WorkerProfileRepository;
import com.kazikonnect.backend.features.worker.WorkerStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.kazikonnect.backend.features.common.Notification;
import com.kazikonnect.backend.features.common.NotificationRepository;
import com.kazikonnect.backend.features.common.Message;
import com.kazikonnect.backend.features.common.MessageRepository;
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
    private final AuthRepository authRepository;
    private final ClientProfileRepository clientProfileRepository;
    private final AdminLogRepository adminLogRepository;
    private final NotificationRepository notificationRepository;
    private final MessageRepository messageRepository;

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
    @org.springframework.transaction.annotation.Transactional
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

        // Log the action for audit
        adminLogRepository.save(AdminLog.builder()
                .adminId(adminId)
                .action("APPROVE_WORKER")
                .targetId(id)
                .build());

        // Notify Worker
        notificationRepository.save(Notification.builder()
                .user(worker.getUser())
                .title("Profile Approved! 🎉")
                .message("Congratulations! Your profile has been verified and is now live on the platform.")
                .type("SUCCESS")
                .build());

        return ResponseEntity.ok(java.util.Map.of(
                "message", "Worker " + worker.getFullName() + " verified and is now live!",
                "status", "APPROVED"));
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

        // Log the action for audit
        adminLogRepository.save(AdminLog.builder()
                .adminId(adminId)
                .action("REJECT_WORKER")
                .targetId(id)
                .build());

        // Notify Worker
        notificationRepository.save(Notification.builder()
                .user(worker.getUser())
                .title("Verification Update")
                .message("Your profile verification was unsuccessful. Reason: " + reason)
                .type("WARNING")
                .build());

        // Also send an actual Message so it appears in the chat
        userRepository.findById(adminId).ifPresent(adminUser -> {
            messageRepository.save(Message.builder()
                    .sender(adminUser)
                    .receiver(worker.getUser())
                    .content("System: Your profile verification was unsuccessful. Reason: " + reason)
                    .sentAt(LocalDateTime.now())
                    .isRead(false)
                    .build());
        });

        return ResponseEntity.ok(java.util.Map.of(
                "message", "Worker rejected. Reason: " + reason,
                "status", "REJECTED"));
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
            boolean isActive = authRepository.findByUserId(u.getId()).map(a -> a.isActive()).orElse(true);
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("email", u.getEmail());
            m.put("fullName", u.getFullName());
            m.put("role", u.getRole());
            m.put("active", isActive);
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

    @PutMapping("/users/{id}/suspend")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> suspendUser(@PathVariable UUID id) {
        if (!userRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        var auth = authRepository.findByUserId(id).orElse(null);
        if (auth == null) {
            return ResponseEntity.badRequest().body("Auth record not found.");
        }
        auth.setActive(false);
        authRepository.save(auth);

        // Notify User
        notificationRepository.save(Notification.builder()
                .user(userRepository.findById(id).orElse(null))
                .title("Account Suspended")
                .message("Your account has been suspended by an administrator. Please contact support.")
                .type("WARNING")
                .build());

        return ResponseEntity.ok("User suspended successfully.");
    }

    @PutMapping("/users/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> activateUser(@PathVariable UUID id) {
        if (!userRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        var auth = authRepository.findByUserId(id).orElse(null);
        if (auth == null) {
            return ResponseEntity.badRequest().body("Auth record not found.");
        }
        auth.setActive(true);
        authRepository.save(auth);
        return ResponseEntity.ok("User activated successfully.");
    }

    @PutMapping("/users/{id}/name")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateUserName(@PathVariable UUID id, @RequestParam String fullName) {
        return userRepository.findById(id).map(user -> {
            user.setFullName(fullName);
            userRepository.save(user);
            return ResponseEntity.ok("User name updated.");
        }).orElse(ResponseEntity.notFound().build());
    }

    // ==================== ACTIVITY LOGS ====================

    @GetMapping("/logs")
    @PreAuthorize("hasRole('ADMIN')")
    public List<java.util.Map<String, Object>> getAdminLogs() {
        return adminLogRepository.findAll().stream()
                .sorted(java.util.Comparator.comparing(AdminLog::getCreatedAt).reversed())
                .map(log -> {
                    java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("id", log.getId());
                    m.put("adminId", log.getAdminId());
                    m.put("action", log.getAction());
                    m.put("targetId", log.getTargetId());
                    m.put("createdAt", log.getCreatedAt());
                    return m;
                }).toList();
    }
}
