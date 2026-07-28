package com.kazikonnect.backend.features.common;

import com.kazikonnect.backend.features.auth.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

/**
 * Single entry point for creating notifications.
 *
 * <p>Persisting a row is not enough on its own — the client only learns about a
 * notification when it is pushed over STOMP, so every write goes through here to
 * guarantee the two stay in step. Business code must not call
 * {@link NotificationRepository#save} directly for new notifications.
 *
 * <p>The push is deferred until after commit. Notifications are almost always
 * created inside a transactional business operation, and pushing inline would
 * announce work that a later rollback discards.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    /** STOMP destination suffix; resolves to /user/{userId}/queue/notifications per subscriber. */
    static final String USER_QUEUE = "/queue/notifications";

    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public Notification dispatch(User user, String title, String message, String type) {
        return dispatch(Notification.builder()
                .user(user)
                .title(title)
                .message(message)
                .type(type)
                .build());
    }

    /**
     * Persists the notification and pushes it to its recipient once the surrounding
     * transaction commits.
     */
    public Notification dispatch(Notification notification) {
        if (notification.getUser() == null) {
            // Without a recipient there is nobody to notify and the row violates a
            // not-null constraint. Drop it rather than failing the business operation.
            log.warn("Discarding notification with no recipient: {}", notification.getTitle());
            return notification;
        }

        Notification saved = notificationRepository.save(notification);
        pushAfterCommit(saved);
        return saved;
    }

    public List<Notification> dispatchAll(List<Notification> notifications) {
        return notifications.stream().map(this::dispatch).toList();
    }

    private void pushAfterCommit(Notification saved) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    push(saved);
                }
            });
        } else {
            push(saved);
        }
    }

    private void push(Notification saved) {
        String recipientId = saved.getUser().getId().toString();
        try {
            messagingTemplate.convertAndSendToUser(recipientId, USER_QUEUE, NotificationDTO.from(saved));
        } catch (RuntimeException ex) {
            // A dead socket must never roll back or fail the business operation that
            // triggered it. The row is already persisted, so the client still picks it
            // up on its next fetch.
            log.warn("Failed to push notification {} to user {}: {}", saved.getId(), recipientId, ex.getMessage());
        }
    }
}
