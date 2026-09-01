package com.ipsakti.ip_sakti_backend.conversation.repository;

import com.ipsakti.ip_sakti_backend.conversation.entity.ConversationEntity;
import com.ipsakti.ip_sakti_backend.conversation.entity.UserEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ConversationRepository extends JpaRepository<ConversationEntity, UUID> {

    Page<ConversationEntity> findByUserOrderByUpdatedAtDesc(UserEntity user, Pageable pageable);

    @Query("SELECT c FROM ConversationEntity c WHERE c.id = :id AND c.user.id = :userId")
    Optional<ConversationEntity> findByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    @Query("SELECT c FROM ConversationEntity c WHERE c.id = :id AND c.user.externalAuthId = :externalAuthId")
    Optional<ConversationEntity> findByIdAndExternalAuthId(@Param("id") UUID id, @Param("externalAuthId") String externalAuthId);
}
