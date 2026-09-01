package com.ipsakti.ip_sakti_backend.auth;

import com.ipsakti.ip_sakti_backend.conversation.entity.UserEntity;
import com.ipsakti.ip_sakti_backend.conversation.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public UserEntity getOrCreateUser(String externalAuthId, String email, String displayName) {
        if (externalAuthId == null || externalAuthId.isBlank()) {
            throw new IllegalArgumentException("externalAuthId cannot be blank");
        }

        return userRepository.findByExternalAuthId(externalAuthId)
                .map(user -> {
                    boolean modified = false;
                    if (email != null && !email.isBlank() && !email.equals(user.getEmail())) {
                        user.setEmail(email);
                        modified = true;
                    }
                    if (displayName != null && !displayName.isBlank() && !displayName.equals(user.getDisplayName())) {
                        user.setDisplayName(displayName);
                        modified = true;
                    }
                    return modified ? userRepository.save(user) : user;
                })
                .orElseGet(() -> {
                    UserEntity newUser = new UserEntity(externalAuthId, email, displayName);
                    UserEntity saved = userRepository.save(newUser);
                    log.info("user_created externalAuthId={} userId={}", externalAuthId, saved.getId());
                    return saved;
                });
    }

    @Transactional(readOnly = true)
    public Optional<UserEntity> findByExternalAuthId(String externalAuthId) {
        return userRepository.findByExternalAuthId(externalAuthId);
    }

    @Transactional(readOnly = true)
    public Optional<UserEntity> findById(UUID id) {
        return userRepository.findById(id);
    }
}
