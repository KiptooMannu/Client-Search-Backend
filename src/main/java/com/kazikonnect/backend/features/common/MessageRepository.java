package com.kazikonnect.backend.features.common;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.kazikonnect.backend.features.auth.User;
import java.util.List;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {
    List<Message> findAllBySenderIdAndReceiverIdOrderBySentAtAsc(UUID senderId, UUID receiverId);
    
    @Query("SELECT m FROM Message m WHERE (m.sender.id = :u1 AND m.receiver.id = :u2) OR (m.sender.id = :u2 AND m.receiver.id = :u1) ORDER BY m.sentAt DESC")
    Page<Message> findConversation(UUID u1, UUID u2, Pageable pageable);

    @Query("SELECT DISTINCT m FROM Message m LEFT JOIN FETCH m.sender LEFT JOIN FETCH m.receiver WHERE m.sentAt IN (SELECT MAX(m2.sentAt) FROM Message m2 WHERE m2.sender.id = :userId OR m2.receiver.id = :userId GROUP BY CASE WHEN m2.sender.id = :userId THEN m2.receiver.id ELSE m2.sender.id END) ORDER BY m.sentAt DESC")
    List<Message> findRecentConversations(UUID userId);
    
    List<Message> findBySenderIdAndReceiverIdAndIsReadFalse(UUID senderId, UUID receiverId);
    
    @Query("SELECT DISTINCT u FROM User u WHERE (u.id IN (SELECT m.sender.id FROM Message m WHERE m.receiver.id = :userId) OR u.id IN (SELECT m.receiver.id FROM Message m WHERE m.sender.id = :userId)) AND u.id != :userId ORDER BY u.username ASC")
    Page<User> findConversationPartners(UUID userId, Pageable pageable);
    
    @Query("SELECT DISTINCT u FROM User u WHERE (u.id IN (SELECT m.sender.id FROM Message m WHERE m.receiver.id = :userId) OR u.id IN (SELECT m.receiver.id FROM Message m WHERE m.sender.id = :userId)) AND u.id != :userId AND (LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))) ORDER BY u.username ASC")
    Page<User> searchConversationPartners(UUID userId, String search, Pageable pageable);
}
