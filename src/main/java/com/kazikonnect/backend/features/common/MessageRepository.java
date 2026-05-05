package com.kazikonnect.backend.features.common;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {
    List<Message> findAllBySenderIdAndReceiverIdOrderBySentAtAsc(UUID senderId, UUID receiverId);
    
    @Query("SELECT m FROM Message m WHERE (m.sender.id = :u1 AND m.receiver.id = :u2) OR (m.sender.id = :u2 AND m.receiver.id = :u1) ORDER BY m.sentAt ASC")
    List<Message> findConversation(UUID u1, UUID u2);

    @Query("SELECT m FROM Message m WHERE m.sentAt IN (SELECT MAX(m2.sentAt) FROM Message m2 WHERE m2.sender.id = :userId OR m2.receiver.id = :userId GROUP BY CASE WHEN m2.sender.id = :userId THEN m2.receiver.id ELSE m2.sender.id END) ORDER BY m.sentAt DESC")
    List<Message> findRecentConversations(UUID userId);
}
