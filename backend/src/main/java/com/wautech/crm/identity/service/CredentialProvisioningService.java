package com.wautech.crm.identity.service;

import com.wautech.crm.identity.entity.User;
import com.wautech.crm.identity.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Internal one-time credential provisioning; intentionally has no unauthenticated HTTP endpoint. */
@Service
@Transactional
public class CredentialProvisioningService {
    private static final int MIN_PASSWORD_BYTES = 12;
    private static final int MAX_BCRYPT_BYTES = 72;

    private final UserRepository repository;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    public CredentialProvisioningService(UserRepository repository, UserService userService, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
    }

    public void provisionInitialPassword(UUID userId, String rawPassword) {
        int bytes = rawPassword == null ? 0 : rawPassword.getBytes(StandardCharsets.UTF_8).length;
        if (bytes < MIN_PASSWORD_BYTES || bytes > MAX_BCRYPT_BYTES) throw new InvalidPasswordException();

        User user = userService.requireUser(userId);
        if (user.getPasswordHash() != null) throw new CredentialAlreadyProvisionedException(userId);
        int updatedRows = repository.provisionPasswordHashIfMissing(userId, passwordEncoder.encode(rawPassword));
        if (updatedRows != 1) throw new CredentialAlreadyProvisionedException(userId);
    }
}
