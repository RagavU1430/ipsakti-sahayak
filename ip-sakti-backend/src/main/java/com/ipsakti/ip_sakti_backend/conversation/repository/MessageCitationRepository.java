package com.ipsakti.ip_sakti_backend.conversation.repository;

import com.ipsakti.ip_sakti_backend.conversation.entity.MessageCitationEntity;
import com.ipsakti.ip_sakti_backend.conversation.entity.MessageEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MessageCitationRepository extends JpaRepository<MessageCitationEntity, UUID> {
    List<MessageCitationEntity> findByMessageOrderByOrdinalAsc(MessageEntity message);
}
