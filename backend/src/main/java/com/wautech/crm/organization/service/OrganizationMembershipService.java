package com.wautech.crm.organization.service;

import com.wautech.crm.identity.entity.User;
import com.wautech.crm.identity.service.UserService;
import com.wautech.crm.organization.dto.OrganizationMembershipResponse;
import com.wautech.crm.organization.entity.MembershipStatus;
import com.wautech.crm.organization.entity.OrganizationMembership;
import com.wautech.crm.organization.repository.OrganizationMembershipRepository;
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

    public OrganizationMembershipService(OrganizationMembershipRepository membershipRepository,
            OrganizationService organizationService, UserService userService) {
        this.membershipRepository = membershipRepository;
        this.organizationService = organizationService;
        this.userService = userService;
    }

    public OrganizationMembershipResponse create(UUID organizationId, UUID userId) {
        var organization = organizationService.requireActiveOrganization(organizationId);
        User user = userService.requireUser(userId);
        if (membershipRepository.existsByOrganization_IdAndUser_Id(organizationId, userId)) {
            throw new DuplicateOrganizationMembershipException(organizationId, userId);
        }
        return OrganizationMembershipResponse.from(membershipRepository.save(new OrganizationMembership(organization, user)));
    }

    @Transactional(readOnly = true)
    public List<OrganizationMembershipResponse> listByOrganization(UUID organizationId) {
        organizationService.requireActiveOrganization(organizationId);
        return membershipRepository.findAllByOrganization_IdOrderByCreatedAtDesc(organizationId).stream()
                .map(OrganizationMembershipResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<OrganizationMembershipResponse> listByUser(UUID organizationId, UUID userId) {
        organizationService.requireActiveOrganization(organizationId);
        userService.requireUser(userId);
        return membershipRepository.findAllByOrganization_IdAndUser_IdOrderByCreatedAtDesc(organizationId, userId).stream()
                .map(OrganizationMembershipResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public OrganizationMembershipResponse get(UUID organizationId, UUID membershipId) {
        organizationService.requireActiveOrganization(organizationId);
        return OrganizationMembershipResponse.from(requireMembership(organizationId, membershipId));
    }

    /** Internal lifecycle operation. It is deliberately not exposed through an HTTP controller. */
    public OrganizationMembershipResponse transition(UUID organizationId, UUID membershipId, MembershipStatus nextStatus) {
        organizationService.requireActiveOrganization(organizationId);
        OrganizationMembership membership = requireMembership(organizationId, membershipId);
        if (nextStatus == MembershipStatus.ACTIVE) userService.requireEnabledUser(membership.getUser().getId());
        membership.transitionTo(nextStatus);
        return OrganizationMembershipResponse.from(membershipRepository.save(membership));
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
