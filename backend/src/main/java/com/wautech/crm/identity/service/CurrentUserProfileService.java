package com.wautech.crm.identity.service;
import com.wautech.crm.identity.dto.SelfProfileRequest;
import com.wautech.crm.identity.dto.UserResponse;
import com.wautech.crm.identity.repository.UserRepository;
import com.wautech.crm.identity.security.CrmUserPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.wautech.crm.audit.AuditedMutation;
@Service
@Transactional
public class CurrentUserProfileService {
    private final UserService users;
    private final UserRepository repository;
    public CurrentUserProfileService(UserService users, UserRepository repository) {
        this.users = users;
        this.repository = repository;
    }
    private com.wautech.crm.identity.entity.User currentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof CrmUserPrincipal principal))
            throw new AccessDeniedException("Authentication is required");
        return users.requireEnabledUser(principal.getId());
    }
    @Transactional(readOnly = true)
    public UserResponse get() { return UserResponse.from(currentUser()); }
    @AuditedMutation(eventType = "USER_PROFILE_UPDATED", targetType = "USER", organizationScoped = false)
    public UserResponse update(SelfProfileRequest request) {
        var user = currentUser();
        user.updateProfile(user.getEmail(), request.firstName().trim(), request.lastName().trim());
        return UserResponse.from(repository.save(user));
    }
}
