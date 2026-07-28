package com.kazikonnect.backend.features.common;

import com.kazikonnect.backend.features.auth.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('Client','Worker','Admin')")
public class NotificationController {

    /** Caps page size so a client cannot request the entire table with ?size=100000. */
    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @GetMapping("/user/{userId}")
    @Transactional(readOnly = true)
    public Page<NotificationDTO> getUserNotifications(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Principal principal) {
        requireSelf(userId, principal);
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
        return notificationRepository.findAllByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(NotificationDTO::from);
    }

    @GetMapping("/user/{userId}/unread-count")
    @Transactional(readOnly = true)
    public Map<String, Long> getUnreadCount(@PathVariable UUID userId, Principal principal) {
        requireSelf(userId, principal);
        return Map.of("unreadCount", notificationRepository.countByUserIdAndIsReadFalse(userId));
    }

    @PutMapping("/{notificationId}/read")
    @Transactional
    public NotificationDTO markAsRead(@PathVariable UUID notificationId, Principal principal) {
        Notification existing = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        if (existing.getUser() != null) {
            requireSelf(existing.getUser().getId(), principal);
        }
        existing.setRead(true);
        return NotificationDTO.from(notificationRepository.save(existing));
    }

    @PutMapping("/user/{userId}/read-all")
    @Transactional
    public Map<String, Integer> markAllAsRead(@PathVariable UUID userId, Principal principal) {
        requireSelf(userId, principal);
        return Map.of("updated", notificationRepository.markAllAsReadForUser(userId));
    }

    @DeleteMapping("/{notificationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void deleteNotification(@PathVariable UUID notificationId, Principal principal) {
        Notification existing = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        if (existing.getUser() != null) {
            requireSelf(existing.getUser().getId(), principal);
        }
        notificationRepository.delete(existing);
    }

    /**
     * Notifications are strictly owner-readable. Throwing keeps the check impossible to
     * forget at a call site, unlike returning a value the caller may ignore.
     */
    private void requireSelf(UUID targetUserId, Principal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        boolean isSelf = userRepository.findByUsername(principal.getName())
                .map(actor -> actor.getId().equals(targetUserId))
                .orElse(false);
        if (!isSelf) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
    }
}
