package com.kazikonnect.backend.features.common;

import com.kazikonnect.backend.features.auth.User;
import com.kazikonnect.backend.features.auth.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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
    private final SimpMessagingTemplate messagingTemplate;

    public record MessageRequest(UUID senderId, UUID receiverId, String content, String attachmentUrl) {}

    // READ: Get conversation partners (paginated)
    @GetMapping("/contacts")
    @PreAuthorize("hasAnyRole('CLIENT', 'WORKER', 'ADMIN')")
    public ResponseEntity<?> getContacts(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size, java.security.Principal principal) {
        User user = userRepository.findByUsername(principal.getName()).orElse(null);
        if (user == null) return ResponseEntity.status(401).body("Unauthorized.");
        
        Pageable pageable = PageRequest.of(page, size);
        Page<User> contacts = messageRepository.findConversationPartners(user.getId(), pageable);
        
        List<UserContactDTO> dto = contacts.stream()
                .map(u -> new UserContactDTO(u.getId(), u.getFullName() != null ? u.getFullName() : u.getUsername(), u.getEmail(), u.getRole() != null ? u.getRole().name() : "USER"))
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(new org.springframework.data.domain.PageImpl<>(dto, pageable, contacts.getTotalElements()));
    }

    // READ: Search conversation partners
    @GetMapping("/contacts/search")
    @PreAuthorize("hasAnyRole('CLIENT', 'WORKER', 'ADMIN')")
    public ResponseEntity<?> searchContacts(@RequestParam String q, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size, java.security.Principal principal) {
        User user = userRepository.findByUsername(principal.getName()).orElse(null);
        if (user == null) return ResponseEntity.status(401).body("Unauthorized.");
        if (q == null || q.trim().isEmpty()) return ResponseEntity.badRequest().body("Search query cannot be empty.");
        
        Pageable pageable = PageRequest.of(page, size);
        Page<User> results = messageRepository.searchConversationPartners(user.getId(), q, pageable);
        
        List<UserContactDTO> dto = results.stream()
                .map(u -> new UserContactDTO(u.getId(), u.getFullName() != null ? u.getFullName() : u.getUsername(), u.getEmail(), u.getRole() != null ? u.getRole().name() : "USER"))
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(new org.springframework.data.domain.PageImpl<>(dto, pageable, results.getTotalElements()));
    }

    // READ: Get all users for messaging (deprecated - use /contacts instead)
    @GetMapping("/users")
    @PreAuthorize("hasAnyRole('CLIENT', 'WORKER', 'ADMIN')")
    @Deprecated
    public List<UserContactDTO> getAllUsersForMessaging() {
        return userRepository.findAll().stream()
                .map(u -> new UserContactDTO(u.getId(), u.getFullName() != null ? u.getFullName() : u.getUsername(), u.getEmail(), u.getRole() != null ? u.getRole().name() : "USER"))
                .collect(Collectors.toList());
    }

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
        message.setAttachmentUrl(request.attachmentUrl());

        Message saved = messageRepository.save(message);
        MessageDTO dto = MessageDTO.from(saved);

        // Broadcast to receiver in real-time
        messagingTemplate.convertAndSendToUser(
            receiver.getId().toString(),
            "/queue/messages",
            dto
        );

        return ResponseEntity.ok(dto);
    }

    // READ: Get a single message by ID
    @GetMapping("/{messageId}")
    @PreAuthorize("hasAnyRole('CLIENT', 'WORKER', 'ADMIN')")
    public ResponseEntity<?> getMessageById(@PathVariable UUID messageId) {
        return messageRepository.findById(messageId).map(m -> 
            ResponseEntity.ok(MessageDTO.from(m))
        ).orElse(ResponseEntity.notFound().build());
    }

    // READ: Get a conversation between two users (paginated)
    @GetMapping("/conversation")
    @PreAuthorize("hasAnyRole('CLIENT', 'WORKER', 'ADMIN')")
    public ResponseEntity<?> getConversation(@RequestParam UUID user1Id, @RequestParam UUID user2Id, 
                                              @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Message> messages = messageRepository.findConversation(user1Id, user2Id, pageable);
        
        List<MessageDTO> dto = messages.getContent().stream()
                .map(MessageDTO::from)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(new org.springframework.data.domain.PageImpl<>(dto, pageable, messages.getTotalElements()));
    }

    // READ: Get a conversation between two users (legacy - backward compatibility)
    @GetMapping("/conversation/legacy")
    @PreAuthorize("hasAnyRole('CLIENT', 'WORKER', 'ADMIN')")
    @Deprecated
    public List<MessageDTO> getConversationLegacy(@RequestParam UUID user1Id, @RequestParam UUID user2Id) {
        Pageable pageable = PageRequest.of(0, 50);
        return messageRepository.findConversation(user1Id, user2Id, pageable).stream()
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

    @PutMapping("/conversation/read")
    @PreAuthorize("hasAnyRole('CLIENT', 'WORKER', 'ADMIN')")
    public ResponseEntity<?> markConversationAsRead(@RequestParam UUID senderId, @RequestParam UUID receiverId) {
        // Mark all messages from sender to receiver as read
        List<Message> unread = messageRepository.findBySenderIdAndReceiverIdAndIsReadFalse(senderId, receiverId);
        unread.forEach(m -> m.setRead(true));
        messageRepository.saveAll(unread);

        // Broadcast Read Receipt to the sender
        messagingTemplate.convertAndSendToUser(
            senderId.toString(),
            "/queue/messages",
            java.util.Map.of(
                "type", "READ_RECEIPT",
                "receiverId", receiverId,
                "timestamp", java.time.LocalDateTime.now().toString()
            )
        );

        return ResponseEntity.ok("Conversation marked as read.");
    }

    // READ: Get recent contact users (those with existing conversations)
    @GetMapping("/recent-contacts/{userId}")
    @PreAuthorize("hasAnyRole('CLIENT', 'WORKER', 'ADMIN')")
    public List<UserContactDTO> getRecentContacts(@PathVariable UUID userId) {
        return messageRepository.findRecentConversations(userId).stream()
                .map(m -> {
                    User other = m.getSender().getId().equals(userId) ? m.getReceiver() : m.getSender();
                    return new UserContactDTO(other.getId(), other.getFullName() != null ? other.getFullName() : other.getUsername(), other.getEmail(), 
                        other.getRole() != null ? other.getRole().name() : "USER");
                })
                .collect(Collectors.toList());
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

    // Real-time: Handle typing indicators
    @PostMapping("/typing")
    @PreAuthorize("hasAnyRole('CLIENT', 'WORKER', 'ADMIN')")
    public ResponseEntity<?> sendTypingIndicator(@RequestParam UUID receiverId, @RequestParam boolean typing, java.security.Principal principal) {
        User sender = userRepository.findByUsername(principal.getName()).orElse(null);
        if (sender == null) return ResponseEntity.status(401).build();

        messagingTemplate.convertAndSendToUser(
            receiverId.toString(),
            "/queue/typing",
            java.util.Map.of("senderId", sender.getId(), "typing", typing)
        );
        return ResponseEntity.ok().build();
    }
}
