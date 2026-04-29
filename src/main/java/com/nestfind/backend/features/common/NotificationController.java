package com.nestfind.backend.features.common;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@SuppressWarnings("null")
public class NotificationController {

    private final NotificationRepository notificationRepository;

    // READ: Get all notifications for a user
    @GetMapping("/user/{userId}")
    @PreAuthorize("hasAnyRole('CLIENT', 'WORKER', 'ADMIN')")
    public List<NotificationDTO> getUserNotifications(@PathVariable UUID userId) {
        return notificationRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(NotificationDTO::from)
                .collect(Collectors.toList());
    }

    // READ: Get unread count
    @GetMapping("/user/{userId}/unread-count")
    @PreAuthorize("hasAnyRole('CLIENT', 'WORKER', 'ADMIN')")
    public ResponseEntity<Long> getUnreadCount(@PathVariable UUID userId) {
        long count = notificationRepository.countByUserIdAndIsReadFalse(userId);
        return ResponseEntity.ok(count);
    }

    // UPDATE: Mark a notification as read
    @PutMapping("/{notificationId}/read")
    @PreAuthorize("hasAnyRole('CLIENT', 'WORKER', 'ADMIN')")
    public ResponseEntity<?> markAsRead(@PathVariable UUID notificationId) {
        return notificationRepository.findById(notificationId).map(existing -> {
            existing.setRead(true);
            return ResponseEntity.ok(NotificationDTO.from(notificationRepository.save(existing)));
        }).orElse(ResponseEntity.notFound().build());
    }

    // UPDATE: Mark all notifications as read for a user
    @PutMapping("/user/{userId}/read-all")
    @PreAuthorize("hasAnyRole('CLIENT', 'WORKER', 'ADMIN')")
    public ResponseEntity<?> markAllAsRead(@PathVariable UUID userId) {
        List<Notification> unread = notificationRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(n -> !n.isRead())
                .collect(Collectors.toList());
        
        unread.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(unread);
        
        return ResponseEntity.ok("All notifications marked as read.");
    }

    // DELETE: Delete a notification
    @DeleteMapping("/{notificationId}")
    @PreAuthorize("hasAnyRole('CLIENT', 'WORKER', 'ADMIN')")
    public ResponseEntity<?> deleteNotification(@PathVariable UUID notificationId) {
        if (!notificationRepository.existsById(notificationId)) {
            return ResponseEntity.notFound().build();
        }
        notificationRepository.deleteById(notificationId);
        return ResponseEntity.ok("Notification deleted successfully.");
    }
}
