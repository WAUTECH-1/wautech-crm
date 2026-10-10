package com.wautech.crm.organization.service;

import com.wautech.crm.identity.entity.User;
import com.wautech.crm.identity.service.UserService;
import com.wautech.crm.organization.dto.OrganizationMembershipResponse;
import com.wautech.crm.organization.entity.MembershipStatus;
import com.wautech.crm.organization.entity.OrganizationMembership;
import com.wautech.crm.organization.entity.OrganizationRole;
import com.wautech.crm.organization.repository.OrganizationMembershipRepository;
import com.wautech.crm.platform.security.CrmAuthorization;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class OrganizationMembershipService {
    private final OrganizationMembershipRepository membershipRepository;
    private final OrganizationService organizationService;
    private final UserService userService;
    private final CrmAuthorization authorization;

    public OrganizationMembershipService(OrganizationMembershipRepository membershipRepository,
            OrganizationService organizationService, UserService userService, CrmAuthorization authorization) {
        this.membershipRepository = membershipRepository;
        this.organizationService = organizationService;
        this.userService = userService;
        this.authorization = authorization;
    }

    @PreAuthorize("@crmAuthorization.canManageOrganization(#p0)")
    public OrganizationMembershipResponse create(UUID organizationId, UUID userId) {
        var organization = organizationService.lockActiveOrganization(organizationId);
        User user = userService.requireUser(userId);
        if (membershipRepository.existsByOrganization_IdAndUser_Id(organizationId, userId)) {
            throw new DuplicateOrganizationMembershipException(organizationId, userId);
        }
        return OrganizationMembershipResponse.from(membershipRepository.save(new OrganizationMembership(organization, user)));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@crmAuthorization.canManageOrganization(#p0)")
    public List<OrganizationMembershipResponse> listByOrganization(UUID organizationId) {
        organizationService.requireActiveOrganization(organizationId);
        return membershipRepository.findAllByOrganization_IdOrderByCreatedAtDesc(organizationId).stream()
                .map(OrganizationMembershipResponse::from).toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@crmAuthorization.canManageOrganization(#p0)")
    public List<OrganizationMembershipResponse> listByUser(UUID organizationId, UUID userId) {
        organizationService.requireActiveOrganization(organizationId);
        userService.requireUser(userId);
        return membershipRepository.findAllByOrganization_IdAndUser_IdOrderByCreatedAtDesc(organizationId, userId).stream()
                .map(OrganizationMembershipResponse::from).toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@crmAuthorization.canManageOrganization(#p0)")
    public OrganizationMembershipResponse get(UUID organizationId, UUID membershipId) {
        organizationService.requireActiveOrganization(organizationId);
        return OrganizationMembershipResponse.from(requireMembership(organizationId, membershipId));
    }

    /** Internal lifecycle operation. It is deliberately not exposed through an HTTP controller. */
    @PreAuthorize("@crmAuthorization.canManageOrganization(#p0)")
    public OrganizationMembershipResponse transition(UUID organizationId, UUID membershipId, MembershipStatus nextStatus) {
        organizationService.lockActiveOrganization(organizationId);
        OrganizationMembership membership = requireMembership(organizationId, membershipId);
        OrganizationRole actorRole = authorization.roleForCurrentUser(organizationId)
                .orElseThrow(OrganizationRoleChangeNotAllowedException::new);
        if (membership.getRole() == OrganizationRole.OWNER && actorRole != OrganizationRole.OWNER) {
            throw new OrganizationRoleChangeNotAllowedException();
        }
        if (membership.getRole() == OrganizationRole.OWNER
                && membership.getStatus() == MembershipStatus.ACTIVE
                && nextStatus != MembershipStatus.ACTIVE
                && membershipRepository.countByOrganization_IdAndStatusAndRole(
                        organizationId, MembershipStatus.ACTIVE, OrganizationRole.OWNER) <= 1) {
            throw new LastOrganizationOwnerException(organizationId);
        }
        if (nextStatus == MembershipStatus.ACTIVE) userService.requireEnabledUser(membership.getUser().getId());
        membership.transitionTo(nextStatus);
        return OrganizationMembershipResponse.from(membershipRepository.save(membership));
    }

    @PreAuthorize("@crmAuthorization.canManageOrganization(#p0)")
    public OrganizationMembershipResponse changeRole(UUID organizationId, UUID membershipId, OrganizationRole nextRole) {
        organizationService.lockActiveOrganization(organizationId);
        OrganizationMembership membership = requireMembership(organizationId, membershipId);
        UUID actorId = authorization.currentUserId();
        OrganizationRole actorRole = authorization.roleForCurrentUser(organizationId)
                .orElseThrow(OrganizationRoleChangeNotAllowedException::new);
        if (nextRole == null || actorId == null || actorId.equals(membership.getUser().getId())
                || (membership.getRole() == OrganizationRole.OWNER && actorRole != OrganizationRole.OWNER)
                || !actorRole.canAssign(nextRole)) {
            throw new OrganizationRoleChangeNotAllowedException();
        }
        if (membership.getRole() == OrganizationRole.OWNER && nextRole != OrganizationRole.OWNER
                && membership.getStatus() == MembershipStatus.ACTIVE
                && membershipRepository.countByOrganization_IdAndStatusAndRole(
                        organizationId, MembershipStatus.ACTIVE, OrganizationRole.OWNER) <= 1) {
            throw new LastOrganizationOwnerException(organizationId);
        }
        membership.changeRole(nextRole);
        return OrganizationMembershipResponse.from(membershipRepository.save(membership));
    }

    @PreAuthorize("@crmAuthorization.isOwner(#p0)")
    public OrganizationMembershipResponse transferOwnership(UUID organizationId, UUID targetMembershipId) {
        organizationService.lockActiveOrganization(organizationId);
        UUID actorId = authorization.currentUserId();
        OrganizationMembership owner = membershipRepository.findByOrganization_IdAndUser_IdAndStatus(
                        organizationId, actorId, MembershipStatus.ACTIVE)
                .filter(membership -> membership.getRole() == OrganizationRole.OWNER)
                .orElseThrow(OrganizationRoleChangeNotAllowedException::new);
        OrganizationMembership target = requireMembership(organizationId, targetMembershipId);
        if (actorId == null || actorId.equals(target.getUser().getId()) || target.getStatus() != MembershipStatus.ACTIVE) {
            throw new OrganizationRoleChangeNotAllowedException();
        }
        userService.requireEnabledUser(target.getUser().getId());
        target.changeRole(OrganizationRole.OWNER);
        owner.changeRole(OrganizationRole.ADMIN);
        membershipRepository.save(owner);
        return OrganizationMembershipResponse.from(membershipRepository.save(target));
    }

    @Transactional(readOnly = true)
    public boolean isActiveMember(UUID organizationId, UUID userId) {
        organizationService.requireActiveOrganization(organizationId);
        User user = userService.requireUser(userId);
        return user.isEnabled() && membershipRepository.existsByOrganization_IdAndUser_IdAndStatus(
                organizationId, userId, MembershipStatus.ACTIVE);
    }

    private OrganizationMembership requireMembership(UUID organizationId, UUID membershipId) {
        return membershipRepository.findByIdAndOrganization_Id(membershipId, organizationId)
                .orElseThrow(() -> new OrganizationMembershipNotFoundException(membershipId));
    }
}
