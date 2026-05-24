package com.kazikonnect.backend.features.common;

import com.kazikonnect.backend.features.auth.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor

public class NotificationController {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @GetMapping("/user/{userId}")
    @PreAuthorize("hasAuthority('Client') or hasAuthority('Worker') or hasAuthority('Admin')")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<?> getUserNotifications(@PathVariable UUID userId, Principal principal) {
        if (!isAuthorized(userId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden: Access denied.");
        }
        List<NotificationDTO> list = notificationRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(NotificationDTO::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    // READ: Get unread count
    @GetMapping("/user/{userId}/unread-count")
    @PreAuthorize("hasAuthority('Client') or hasAuthority('Worker') or hasAuthority('Admin')")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<?> getUnreadCount(@PathVariable UUID userId, Principal principal) {
        if (!isAuthorized(userId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden: Access denied.");
        }
        long count = notificationRepository.countByUserIdAndIsReadFalse(userId);
        return ResponseEntity.ok(count);
    }

    // UPDATE: Mark a notification as read
    @PutMapping("/{notificationId}/read")
    @PreAuthorize("hasAuthority('Client') or hasAuthority('Worker') or hasAuthority('Admin')")
    public ResponseEntity<?> markAsRead(@PathVariable UUID notificationId, Principal principal) {
        return notificationRepository.findById(Objects.requireNonNull(notificationId)).map(existing -> {
            if (existing.getUser() != null && !isAuthorized(existing.getUser().getId(), principal)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden: Access denied.");
            }
            existing.setRead(true);
            return ResponseEntity.ok(NotificationDTO.from(notificationRepository.save(existing)));
        }).orElse(ResponseEntity.notFound().build());
    }

    // UPDATE: Mark all notifications as read for a user
    @PutMapping("/user/{userId}/read-all")
    @PreAuthorize("hasAuthority('Client') or hasAuthority('Worker') or hasAuthority('Admin')")
    public ResponseEntity<?> markAllAsRead(@PathVariable UUID userId, Principal principal) {
        if (!isAuthorized(userId, principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden: Access denied.");
        }
        List<Notification> unread = notificationRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(n -> !n.isRead())
                .collect(Collectors.toList());
        
        unread.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(unread);
        
        return ResponseEntity.ok("All notifications marked as read.");
    }

    // DELETE: Delete a notification
    @DeleteMapping("/{notificationId}")
    @PreAuthorize("hasAuthority('Client') or hasAuthority('Worker') or hasAuthority('Admin')")
    public ResponseEntity<?> deleteNotification(@PathVariable UUID notificationId, Principal principal) {
        return notificationRepository.findById(Objects.requireNonNull(notificationId)).map(existing -> {
            if (existing.getUser() != null && !isAuthorized(existing.getUser().getId(), principal)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden: Access denied.");
            }
            notificationRepository.delete(existing);
            return ResponseEntity.ok("Notification deleted successfully.");
        }).orElse(ResponseEntity.notFound().build());
    }

    private boolean isAuthorized(UUID targetUserId, Principal principal) {
        if (principal == null) return false;
        var actor = userRepository.findByUsername(principal.getName()).orElse(null);
        if (actor == null) return false;
        return actor.getId().equals(targetUserId);
    }
}
