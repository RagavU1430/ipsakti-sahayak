package com.ipsakti.ip_sakti_backend.conversation.repository;

import com.ipsakti.ip_sakti_backend.conversation.entity.UserEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, UUID> {
    Optional<UserEntity> findByExternalAuthId(String externalAuthId);
    boolean existsByExternalAuthId(String externalAuthId);
}
