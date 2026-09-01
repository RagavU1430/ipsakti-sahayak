package com.ipsakti.ip_sakti_backend.conversation.repository;

import com.ipsakti.ip_sakti_backend.conversation.entity.MessageEntity;
import com.ipsakti.ip_sakti_backend.conversation.entity.MessageSourceEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MessageSourceRepository extends JpaRepository<MessageSourceEntity, UUID> {
    List<MessageSourceEntity> findByMessageOrderByOrdinalAsc(MessageEntity message);
}
