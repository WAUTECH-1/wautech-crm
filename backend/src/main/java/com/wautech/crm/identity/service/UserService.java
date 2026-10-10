package com.wautech.crm.identity.service;

import com.wautech.crm.audit.AuditedMutation;
import com.wautech.crm.identity.dto.UserProfileRequest;
import com.wautech.crm.identity.dto.UserResponse;
import com.wautech.crm.identity.entity.User;
import com.wautech.crm.identity.repository.UserRepository;
import com.wautech.crm.organization.entity.MembershipStatus;
import com.wautech.crm.organization.entity.OrganizationMembership;
import com.wautech.crm.organization.entity.OrganizationRole;
import com.wautech.crm.organization.repository.OrganizationMembershipRepository;
import com.wautech.crm.organization.repository.OrganizationRepository;
import com.wautech.crm.organization.service.LastOrganizationOwnerException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
@Transactional
public class UserService {
    private final UserRepository repository;
    private final OrganizationMembershipRepository membershipRepository;
    private final OrganizationRepository organizationRepository;

    public UserService(UserRepository repository, OrganizationMembershipRepository membershipRepository,
            OrganizationRepository organizationRepository) {
        this.repository = repository;
        this.membershipRepository = membershipRepository;
        this.organizationRepository = organizationRepository;
    }

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

    @AuditedMutation(eventType = "USER_ACTIVATION_CHANGED", targetType = "USER", organizationScoped = false)
    public UserResponse setEnabled(UUID id, boolean enabled) {
        if (!enabled) ensureNotLastOwner(id);
        User user = requireUser(id);
        user.setEnabled(enabled);
        return UserResponse.from(repository.save(user));
    }

    private void ensureNotLastOwner(UUID userId) {
        for (OrganizationMembership membership : membershipRepository.findAllByUser_IdAndStatusAndRole(
                userId, MembershipStatus.ACTIVE, OrganizationRole.OWNER)) {
            UUID organizationId = membership.getOrganization().getId();
            if (organizationRepository.lockByIdAndArchivedFalse(organizationId).isEmpty()) continue;
            if (membershipRepository.countByOrganization_IdAndStatusAndRole(
                    organizationId, MembershipStatus.ACTIVE, OrganizationRole.OWNER) <= 1) {
                throw new LastOrganizationOwnerException(organizationId);
            }
        }
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
