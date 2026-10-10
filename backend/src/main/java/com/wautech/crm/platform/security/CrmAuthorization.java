package com.wautech.crm.platform.security;

import com.wautech.crm.identity.security.CrmUserPrincipal;
import com.wautech.crm.identity.repository.UserRepository;
import com.wautech.crm.organization.entity.MembershipStatus;
import com.wautech.crm.organization.entity.OrganizationMembership;
import com.wautech.crm.organization.entity.OrganizationRole;
import com.wautech.crm.organization.repository.OrganizationMembershipRepository;
import com.wautech.crm.organization.repository.OrganizationRepository;
import com.wautech.crm.platform.tenant.AuthenticatedOrganizationContext;
import com.wautech.crm.platform.tenant.OrganizationContextUnavailableException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/** Central method-security policy. All decisions use the authenticated principal and persisted membership role. */
@Component("crmAuthorization")
public class CrmAuthorization {
    private final AuthenticatedOrganizationContext organizationContext;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMembershipRepository membershipRepository;
    private final UserRepository userRepository;

    public CrmAuthorization(AuthenticatedOrganizationContext organizationContext,
            OrganizationRepository organizationRepository, OrganizationMembershipRepository membershipRepository,
            UserRepository userRepository) {
        this.organizationContext = organizationContext;
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
    }

    public boolean canView(UUID organizationId) {
        return roleFor(organizationId).map(OrganizationRole::canViewCrm).orElse(false);
    }

    public boolean canWrite(UUID organizationId) {
        return roleFor(organizationId).map(OrganizationRole::canWriteCrm).orElse(false);
    }

    public boolean canManageOrganization(UUID organizationId) {
        return roleFor(organizationId).map(OrganizationRole::canManageOrganization).orElse(false);
    }

    public boolean isOwner(UUID organizationId) {
        return roleFor(organizationId).filter(role -> role == OrganizationRole.OWNER).isPresent();
    }

    public Optional<OrganizationRole> roleForCurrentUser(UUID organizationId) {
        return roleFor(organizationId);
    }

    public UUID currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof CrmUserPrincipal principal)) return null;
        return principal.getId();
    }

    private Optional<OrganizationRole> roleFor(UUID organizationId) {
        UUID userId = currentUserId();
        if (userId == null || organizationId == null) return Optional.empty();
        try {
            if (!organizationId.equals(organizationContext.requireOrganizationId())
                    || !organizationRepository.existsByIdAndArchivedFalse(organizationId)) return Optional.empty();
        } catch (OrganizationContextUnavailableException missingTrustedContext) {
            return Optional.empty();
        }
        if (userRepository.findById(userId).filter(user -> user.isEnabled()).isEmpty()) return Optional.empty();
        return membershipRepository.findByOrganization_IdAndUser_IdAndStatus(
                        organizationId, userId, MembershipStatus.ACTIVE)
                .map(OrganizationMembership::getRole);
    }
}
