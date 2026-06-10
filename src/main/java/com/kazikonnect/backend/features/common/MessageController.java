package com.kazikonnect.backend.features.common;

import com.kazikonnect.backend.features.auth.User;
import com.kazikonnect.backend.features.auth.UserRepository;
import com.kazikonnect.backend.features.auth.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import java.security.Principal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public record MessageRequest(UUID senderId, UUID receiverId, String content, String attachmentUrl) {
    }

    // READ: Get conversation partners (paginated)
    @GetMapping("/contacts")
    @PreAuthorize("hasAuthority('Client') or hasAuthority('Worker') or hasAuthority('Admin')")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<?> getContacts(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, Principal principal) {
        User user = userRepository.findByUsername(principal.getName()).orElse(null);
        if (user == null)
            return ResponseEntity.status(401).body("Unauthorized.");

        Pageable pageable = PageRequest.of(page, size);
        Page<User> contacts = messageRepository.findConversationPartners(user.getId(), pageable);

        List<UserContactDTO> dto = contacts.stream()
                .map(u -> new UserContactDTO(u.getId(), u.getFullName() != null ? u.getFullName() : u.getUsername(),
                        u.getEmail(), u.getRole() != null ? u.getRole().name() : "USER"))
                .collect(Collectors.toList());

        return ResponseEntity.ok(new org.springframework.data.domain.PageImpl<>(Objects.requireNonNull(dto), pageable,
                contacts.getTotalElements()));
    }

    // READ: Search conversation partners
    @GetMapping("/contacts/search")
    @PreAuthorize("hasAuthority('Client') or hasAuthority('Worker') or hasAuthority('Admin')")
    public ResponseEntity<?> searchContacts(@RequestParam String q, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, Principal principal) {
        User user = userRepository.findByUsername(principal.getName()).orElse(null);
        if (user == null)
            return ResponseEntity.status(401).body("Unauthorized.");
        if (q == null || q.trim().isEmpty())
            return ResponseEntity.badRequest().body("Search query cannot be empty.");

        Pageable pageable = PageRequest.of(page, size);
        Page<User> results = messageRepository.searchConversationPartners(user.getId(), q, pageable);

        List<UserContactDTO> dto = results.stream()
                .map(u -> new UserContactDTO(u.getId(), u.getFullName() != null ? u.getFullName() : u.getUsername(),
                        u.getEmail(), u.getRole() != null ? u.getRole().name() : "USER"))
                .collect(Collectors.toList());

        return ResponseEntity.ok(new org.springframework.data.domain.PageImpl<>(Objects.requireNonNull(dto), pageable,
                results.getTotalElements()));
    }

    // READ: Get all users for messaging (deprecated - use /contacts instead)
    @GetMapping("/users")
    @PreAuthorize("hasAuthority('Admin')")
    @Deprecated
    public List<UserContactDTO> getAllUsersForMessaging() {
        return userRepository.findAll().stream()
                .map(u -> new UserContactDTO(u.getId(), u.getFullName() != null ? u.getFullName() : u.getUsername(),
                        u.getEmail(), u.getRole() != null ? u.getRole().name() : "USER"))
                .collect(Collectors.toList());
    }

    // CREATE: Send a message
    @PostMapping
    @PreAuthorize("hasAuthority('Client') or hasAuthority('Worker') or hasAuthority('Admin')")
    public ResponseEntity<?> sendMessage(@RequestBody MessageRequest request, Principal principal) {
        User actor = userRepository.findByUsername(principal.getName()).orElse(null);
        if (actor == null)
            return ResponseEntity.status(401).body("Unauthorized.");

        if (!actor.getId().equals(request.senderId()) && actor.getRole() != UserRole.ADMIN) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden: Cannot spoof message sender.");
        }

        User sender = userRepository.findById(Objects.requireNonNull(request.senderId())).orElse(null);
        User receiver = userRepository.findById(Objects.requireNonNull(request.receiverId())).orElse(null);

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

        messagingTemplate.convertAndSendToUser(
                Objects.requireNonNull(receiver.getId().toString()),
                "/queue/messages",
                Objects.requireNonNull(dto));

        return ResponseEntity.ok(dto);
    }

    // READ: Get a single message by ID
    @GetMapping("/{messageId}")
    @PreAuthorize("hasAuthority('Client') or hasAuthority('Worker') or hasAuthority('Admin')")
    public ResponseEntity<?> getMessageById(@PathVariable UUID messageId, Principal principal) {
        User actor = userRepository.findByUsername(principal.getName()).orElse(null);
        if (actor == null)
            return ResponseEntity.status(401).body("Unauthorized.");

        return messageRepository.findById(Objects.requireNonNull(messageId)).map(m -> {
            boolean isAdmin = actor.getRole() == UserRole.ADMIN;
            boolean isParticipant = (m.getSender() != null && m.getSender().getId().equals(actor.getId())) ||
                    (m.getReceiver() != null && m.getReceiver().getId().equals(actor.getId()));
            if (!isAdmin && !isParticipant) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden: Access denied.");
            }
            return ResponseEntity.ok(MessageDTO.from(m));
        }).orElse(ResponseEntity.notFound().build());
    }

    // READ: Get a conversation between two users (paginated)
    @GetMapping("/conversation")
    @PreAuthorize("hasAuthority('Client') or hasAuthority('Worker') or hasAuthority('Admin')")
    public ResponseEntity<?> getConversation(@RequestParam UUID user1Id, @RequestParam UUID user2Id,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size,
            Principal principal) {
        User actor = userRepository.findByUsername(principal.getName()).orElse(null);
        if (actor == null)
            return ResponseEntity.status(401).body("Unauthorized.");

        boolean isAdmin = actor.getRole() == UserRole.ADMIN;
        boolean isParticipant = actor.getId().equals(user1Id) || actor.getId().equals(user2Id);
        if (!isAdmin && !isParticipant) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden: Access denied.");
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<Message> messages = messageRepository.findConversation(Objects.requireNonNull(user1Id),
                Objects.requireNonNull(user2Id), pageable);

        List<MessageDTO> dto = messages.getContent().stream()
                .map(MessageDTO::from)
                .collect(Collectors.toList());

        return ResponseEntity.ok(new org.springframework.data.domain.PageImpl<>(Objects.requireNonNull(dto), pageable,
                messages.getTotalElements()));
    }

    // READ: Get a conversation between two users (legacy - backward compatibility)
    @GetMapping("/conversation/legacy")
    @PreAuthorize("hasAuthority('Client') or hasAuthority('Worker') or hasAuthority('Admin')")
    @Deprecated
    public ResponseEntity<?> getConversationLegacy(@RequestParam UUID user1Id, @RequestParam UUID user2Id,
            Principal principal) {
        User actor = userRepository.findByUsername(principal.getName()).orElse(null);
        if (actor == null)
            return ResponseEntity.status(401).body("Unauthorized.");

        boolean isAdmin = actor.getRole() == UserRole.ADMIN;
        boolean isParticipant = actor.getId().equals(user1Id) || actor.getId().equals(user2Id);
        if (!isAdmin && !isParticipant) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden: Access denied.");
        }

        Pageable pageable = PageRequest.of(0, 50);
        List<MessageDTO> list = messageRepository
                .findConversation(Objects.requireNonNull(user1Id), Objects.requireNonNull(user2Id), pageable).stream()
                .map(MessageDTO::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    // READ: Get recent chats for a user
    @GetMapping("/user/{userId}/recent")
    @PreAuthorize("hasAuthority('Client') or hasAuthority('Worker') or hasAuthority('Admin')")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<?> getRecentConversations(@PathVariable UUID userId, Principal principal) {
        User actor = userRepository.findByUsername(principal.getName()).orElse(null);
        if (actor == null)
            return ResponseEntity.status(401).body("Unauthorized.");

        if (!actor.getId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden: Access denied.");
        }

        List<MessageDTO> list = messageRepository.findRecentConversations(Objects.requireNonNull(userId)).stream()
                .map(MessageDTO::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    // UPDATE: Mark a message as read
    @PutMapping("/{messageId}/read")
    @PreAuthorize("hasAuthority('Client') or hasAuthority('Worker') or hasAuthority('Admin')")
    public ResponseEntity<?> markAsRead(@PathVariable UUID messageId, Principal principal) {
        User actor = userRepository.findByUsername(principal.getName()).orElse(null);
        if (actor == null)
            return ResponseEntity.status(401).body("Unauthorized.");

        return messageRepository.findById(Objects.requireNonNull(messageId)).map(existing -> {
            if (existing.getReceiver() == null || !existing.getReceiver().getId().equals(actor.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body("Forbidden: Cannot mark other's message as read.");
            }
            existing.setRead(true);
            return ResponseEntity.ok(MessageDTO.from(messageRepository.save(existing)));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/conversation/read")
    @PreAuthorize("hasAuthority('Client') or hasAuthority('Worker') or hasAuthority('Admin')")
    public ResponseEntity<?> markConversationAsRead(@RequestParam UUID senderId, @RequestParam UUID receiverId,
            Principal principal) {
        User actor = userRepository.findByUsername(principal.getName()).orElse(null);
        if (actor == null)
            return ResponseEntity.status(401).body("Unauthorized.");

        if (!actor.getId().equals(receiverId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Forbidden: Cannot mark conversation as read for another user.");
        }

        // Mark all messages from sender to receiver as read
        List<Message> unread = messageRepository.findBySenderIdAndReceiverIdAndIsReadFalse(
                Objects.requireNonNull(senderId), Objects.requireNonNull(receiverId));
        unread.forEach(m -> m.setRead(true));
        messageRepository.saveAll(unread);

        messagingTemplate.convertAndSendToUser(
                Objects.requireNonNull(senderId.toString()),
                "/queue/messages",
                Objects.requireNonNull(java.util.Map.of(
                        "type", "READ_RECEIPT",
                        "receiverId", receiverId,
                        "timestamp", java.time.LocalDateTime.now().toString())));

        return ResponseEntity.ok("Conversation marked as read.");
    }

    // READ: Get recent contact users (those with existing conversations)
    @GetMapping("/recent-contacts/{userId}")
    @PreAuthorize("hasAuthority('Client') or hasAuthority('Worker') or hasAuthority('Admin')")
    public ResponseEntity<?> getRecentContacts(@PathVariable UUID userId, Principal principal) {
        User actor = userRepository.findByUsername(principal.getName()).orElse(null);
        if (actor == null)
            return ResponseEntity.status(401).body("Unauthorized.");

        if (!actor.getId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden: Access denied.");
        }

        List<UserContactDTO> list = messageRepository.findRecentConversations(Objects.requireNonNull(userId)).stream()
                .map(m -> {
                    User other = m.getSender().getId().equals(userId) ? m.getReceiver() : m.getSender();
                    return new UserContactDTO(other.getId(),
                            other.getFullName() != null ? other.getFullName() : other.getUsername(), other.getEmail(),
                            other.getRole() != null ? other.getRole().name() : "USER");
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    // DELETE: Delete a message
    @DeleteMapping("/{messageId}")
    @PreAuthorize("hasAuthority('Client') or hasAuthority('Worker') or hasAuthority('Admin')")
    public ResponseEntity<?> deleteMessage(@PathVariable UUID messageId, Principal principal) {
        User actor = userRepository.findByUsername(principal.getName()).orElse(null);
        if (actor == null)
            return ResponseEntity.status(401).body("Unauthorized.");

        return messageRepository.findById(Objects.requireNonNull(messageId)).map(m -> {
            boolean isAdmin = actor.getRole() == UserRole.ADMIN;
            boolean isParticipant = (m.getSender() != null && m.getSender().getId().equals(actor.getId())) ||
                    (m.getReceiver() != null && m.getReceiver().getId().equals(actor.getId()));
            if (!isAdmin && !isParticipant) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden: Access denied.");
            }
            messageRepository.delete(m);
            return ResponseEntity.ok("Message deleted successfully.");
        }).orElse(ResponseEntity.notFound().build());
    }

    // Real-time: Handle typing indicators
    @PostMapping("/typing")
    @PreAuthorize("hasAuthority('Client') or hasAuthority('Worker') or hasAuthority('Admin')")
    public ResponseEntity<?> sendTypingIndicator(@RequestParam UUID receiverId, @RequestParam boolean typing,
            Principal principal) {
        User sender = userRepository.findByUsername(principal.getName()).orElse(null);
        if (sender == null)
            return ResponseEntity.status(401).build();

        messagingTemplate.convertAndSendToUser(
                Objects.requireNonNull(receiverId.toString()),
                "/queue/typing",
                Objects.requireNonNull(
                        java.util.Map.of("senderId", Objects.requireNonNull(sender.getId()), "typing", typing)));
        return ResponseEntity.ok().build();
    }
}
