package com.kazikonnect.backend.features.common;

import com.kazikonnect.backend.features.auth.User;
import com.kazikonnect.backend.features.auth.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
@SuppressWarnings("null")
public class MessageController {

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;

    public record MessageRequest(UUID senderId, UUID receiverId, String content) {}

    // CREATE: Send a message
    @PostMapping
    @PreAuthorize("hasAnyRole('CLIENT', 'WORKER', 'ADMIN')")
    public ResponseEntity<?> sendMessage(@RequestBody MessageRequest request) {
        User sender = userRepository.findById(request.senderId()).orElse(null);
        User receiver = userRepository.findById(request.receiverId()).orElse(null);

        if (sender == null || receiver == null) {
            return ResponseEntity.badRequest().body("Sender or Receiver not found.");
        }

        Message message = new Message();
        message.setSender(sender);
        message.setReceiver(receiver);
        message.setContent(request.content());

        Message saved = messageRepository.save(message);
        return ResponseEntity.ok(MessageDTO.from(saved));
    }

    // READ: Get a conversation between two users
    @GetMapping("/conversation")
    @PreAuthorize("hasAnyRole('CLIENT', 'WORKER', 'ADMIN')")
    public List<MessageDTO> getConversation(@RequestParam UUID user1Id, @RequestParam UUID user2Id) {
        return messageRepository.findConversation(user1Id, user2Id).stream()
                .map(MessageDTO::from)
                .collect(Collectors.toList());
    }

    // READ: Get recent chats for a user
    @GetMapping("/user/{userId}/recent")
    @PreAuthorize("hasAnyRole('CLIENT', 'WORKER', 'ADMIN')")
    public List<MessageDTO> getRecentConversations(@PathVariable UUID userId) {
        return messageRepository.findRecentConversations(userId).stream()
                .map(MessageDTO::from)
                .collect(Collectors.toList());
    }

    // UPDATE: Mark a message as read
    @PutMapping("/{messageId}/read")
    @PreAuthorize("hasAnyRole('CLIENT', 'WORKER', 'ADMIN')")
    public ResponseEntity<?> markAsRead(@PathVariable UUID messageId) {
        return messageRepository.findById(messageId).map(existing -> {
            existing.setRead(true);
            return ResponseEntity.ok(MessageDTO.from(messageRepository.save(existing)));
        }).orElse(ResponseEntity.notFound().build());
    }

    // DELETE: Delete a message
    @DeleteMapping("/{messageId}")
    @PreAuthorize("hasAnyRole('CLIENT', 'WORKER', 'ADMIN')")
    public ResponseEntity<?> deleteMessage(@PathVariable UUID messageId) {
        if (!messageRepository.existsById(messageId)) {
            return ResponseEntity.notFound().build();
        }
        messageRepository.deleteById(messageId);
        return ResponseEntity.ok("Message deleted successfully.");
    }
}
