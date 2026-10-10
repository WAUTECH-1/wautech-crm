package com.wautech.crm.identity.service;

import com.wautech.crm.identity.dto.UserProfileRequest;
import com.wautech.crm.identity.dto.UserResponse;
import com.wautech.crm.identity.entity.User;
import com.wautech.crm.identity.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
@Transactional
public class UserService {
    private final UserRepository repository;

    public UserService(UserRepository repository) { this.repository = repository; }

    public UserResponse create(UserProfileRequest request) {
        String email = normalizeEmail(request.email());
        rejectDuplicateEmail(email, null);
        return UserResponse.from(repository.save(new User(email, request.firstName().trim(), request.lastName().trim())));
    }

    @Transactional(readOnly = true)
    public UserResponse getById(UUID id) { return UserResponse.from(requireUser(id)); }

    public UserResponse updateProfile(UUID id, UserProfileRequest request) {
        User user = requireUser(id);
        String email = normalizeEmail(request.email());
        rejectDuplicateEmail(email, id);
        user.updateProfile(email, request.firstName().trim(), request.lastName().trim());
        return UserResponse.from(repository.save(user));
    }

    public UserResponse setEnabled(UUID id, boolean enabled) {
        User user = requireUser(id);
        user.setEnabled(enabled);
        return UserResponse.from(repository.save(user));
    }

    public User requireUser(UUID id) { return repository.findById(id).orElseThrow(() -> new UserNotFoundException(id)); }

    public User requireEnabledUser(UUID id) {
        User user = requireUser(id);
        if (!user.isEnabled()) throw new DisabledUserException(id);
        return user;
    }

    private void rejectDuplicateEmail(String email, UUID currentId) {
        boolean duplicate = currentId == null ? repository.existsByEmail(email)
                : repository.existsByEmailAndIdNot(email, currentId);
        if (duplicate) {
            throw new DuplicateUserEmailException(email);
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
