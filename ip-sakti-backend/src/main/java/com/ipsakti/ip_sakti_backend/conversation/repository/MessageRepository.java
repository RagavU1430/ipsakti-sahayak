package com.ipsakti.ip_sakti_backend.conversation.repository;

import com.ipsakti.ip_sakti_backend.conversation.entity.ConversationEntity;
import com.ipsakti.ip_sakti_backend.conversation.entity.MessageEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MessageRepository extends JpaRepository<MessageEntity, UUID> {

    List<MessageEntity> findByConversationOrderByCreatedAtAsc(ConversationEntity conversation);

    Page<MessageEntity> findByConversationOrderByCreatedAtAsc(ConversationEntity conversation, Pageable pageable);

    void deleteByConversation(ConversationEntity conversation);
}
