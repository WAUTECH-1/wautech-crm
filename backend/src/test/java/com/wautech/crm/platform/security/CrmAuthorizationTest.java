package com.wautech.crm.platform.security;

import com.wautech.crm.identity.entity.User;
import com.wautech.crm.identity.repository.UserRepository;
import com.wautech.crm.organization.entity.MembershipStatus;
import com.wautech.crm.organization.entity.Organization;
import com.wautech.crm.organization.entity.OrganizationMembership;
import com.wautech.crm.organization.entity.OrganizationRole;
import com.wautech.crm.organization.repository.OrganizationMembershipRepository;
import com.wautech.crm.organization.repository.OrganizationRepository;
import com.wautech.crm.platform.tenant.AuthenticatedOrganizationContext;
import com.wautech.crm.platform.tenant.OrganizationContextUnavailableException;
import com.wautech.crm.platform.test.SecurityTestIdentity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CrmAuthorizationTest {
    private final AuthenticatedOrganizationContext organizationContext = mock(AuthenticatedOrganizationContext.class);
    private final OrganizationRepository organizationRepository = mock(OrganizationRepository.class);
    private final OrganizationMembershipRepository membershipRepository = mock(OrganizationMembershipRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final CrmAuthorization authorization = new CrmAuthorization(
            organizationContext, organizationRepository, membershipRepository, userRepository);

    @AfterEach
    void clearSecurityContext() { SecurityContextHolder.clearContext(); }

    @Test
    void persistedMembershipRoleControlsActionsWithinTrustedOrganization() {
        authenticate();
        allowActiveOrganizationAndUser(SecurityTestIdentity.ORGANIZATION_ID);
        when(membershipRepository.findByOrganization_IdAndUser_IdAndStatus(
                SecurityTestIdentity.ORGANIZATION_ID, SecurityTestIdentity.USER_ID, MembershipStatus.ACTIVE))
                .thenReturn(Optional.of(activeMembership(OrganizationRole.VIEWER)));

        assertTrue(authorization.canView(SecurityTestIdentity.ORGANIZATION_ID));
        assertFalse(authorization.canWrite(SecurityTestIdentity.ORGANIZATION_ID));
        assertFalse(authorization.canManageOrganization(SecurityTestIdentity.ORGANIZATION_ID));
    }

    @Test
    void rejectsMismatchedOrganizationAndMissingTrustedTenant() {
        authenticate();
        allowActiveOrganizationAndUser(SecurityTestIdentity.ORGANIZATION_ID);
        when(organizationContext.requireOrganizationId()).thenReturn(UUID.randomUUID());
        assertFalse(authorization.canView(SecurityTestIdentity.ORGANIZATION_ID));

        when(organizationContext.requireOrganizationId()).thenThrow(new OrganizationContextUnavailableException());
        assertFalse(authorization.canWrite(SecurityTestIdentity.ORGANIZATION_ID));
        verifyNoInteractions(membershipRepository);
    }

    @Test
    void rejectsDisabledUsersAndNonActiveMemberships() {
        authenticate();
        when(organizationContext.requireOrganizationId()).thenReturn(SecurityTestIdentity.ORGANIZATION_ID);
        when(organizationRepository.existsByIdAndArchivedFalse(SecurityTestIdentity.ORGANIZATION_ID)).thenReturn(true);
        when(userRepository.findById(SecurityTestIdentity.USER_ID)).thenReturn(Optional.empty());
        assertFalse(authorization.canView(SecurityTestIdentity.ORGANIZATION_ID));
        verifyNoInteractions(membershipRepository);
    }

    @Test
    void rejectsDisabledAccountAndSuspendedMembership() {
        authenticate();
        when(organizationContext.requireOrganizationId()).thenReturn(SecurityTestIdentity.ORGANIZATION_ID);
        when(organizationRepository.existsByIdAndArchivedFalse(SecurityTestIdentity.ORGANIZATION_ID)).thenReturn(true);
        User disabled = mock(User.class);
        when(disabled.isEnabled()).thenReturn(false);
        when(userRepository.findById(SecurityTestIdentity.USER_ID)).thenReturn(Optional.of(disabled));
        assertFalse(authorization.canView(SecurityTestIdentity.ORGANIZATION_ID));
        verifyNoInteractions(membershipRepository);

        User enabled = SecurityTestIdentity.user();
        when(userRepository.findById(SecurityTestIdentity.USER_ID)).thenReturn(Optional.of(enabled));
        when(membershipRepository.findByOrganization_IdAndUser_IdAndStatus(
                SecurityTestIdentity.ORGANIZATION_ID, SecurityTestIdentity.USER_ID, MembershipStatus.ACTIVE))
                .thenReturn(Optional.empty());
        assertFalse(authorization.canView(SecurityTestIdentity.ORGANIZATION_ID));
    }

    @Test
    void oneUserReceivesDifferentPersistedRolesPerOrganization() {
        UUID secondOrganizationId = UUID.randomUUID();
        authenticate();
        when(organizationContext.requireOrganizationId()).thenReturn(SecurityTestIdentity.ORGANIZATION_ID, secondOrganizationId);
        when(organizationRepository.existsByIdAndArchivedFalse(any())).thenReturn(true);
        when(userRepository.findById(SecurityTestIdentity.USER_ID)).thenReturn(Optional.of(SecurityTestIdentity.user()));
        when(membershipRepository.findByOrganization_IdAndUser_IdAndStatus(
                SecurityTestIdentity.ORGANIZATION_ID, SecurityTestIdentity.USER_ID, MembershipStatus.ACTIVE))
                .thenReturn(Optional.of(activeMembership(OrganizationRole.ADMIN)));
        when(membershipRepository.findByOrganization_IdAndUser_IdAndStatus(
                secondOrganizationId, SecurityTestIdentity.USER_ID, MembershipStatus.ACTIVE))
                .thenReturn(Optional.of(activeMembership(OrganizationRole.VIEWER)));

        assertTrue(authorization.canWrite(SecurityTestIdentity.ORGANIZATION_ID));
        assertFalse(authorization.canWrite(secondOrganizationId));
        verify(membershipRepository).findByOrganization_IdAndUser_IdAndStatus(
                SecurityTestIdentity.ORGANIZATION_ID, SecurityTestIdentity.USER_ID, MembershipStatus.ACTIVE);
        verify(membershipRepository).findByOrganization_IdAndUser_IdAndStatus(
                secondOrganizationId, SecurityTestIdentity.USER_ID, MembershipStatus.ACTIVE);
    }

    @Test
    void rejectsArchivedOrganizationsAndInactiveMemberships() {
        authenticate();
        when(organizationContext.requireOrganizationId()).thenReturn(SecurityTestIdentity.ORGANIZATION_ID);
        when(organizationRepository.existsByIdAndArchivedFalse(SecurityTestIdentity.ORGANIZATION_ID)).thenReturn(false);
        assertFalse(authorization.canView(SecurityTestIdentity.ORGANIZATION_ID));

        when(organizationRepository.existsByIdAndArchivedFalse(SecurityTestIdentity.ORGANIZATION_ID)).thenReturn(true);
        when(userRepository.findById(SecurityTestIdentity.USER_ID)).thenReturn(Optional.of(SecurityTestIdentity.user()));
        when(membershipRepository.findByOrganization_IdAndUser_IdAndStatus(
                SecurityTestIdentity.ORGANIZATION_ID, SecurityTestIdentity.USER_ID, MembershipStatus.ACTIVE))
                .thenReturn(Optional.empty());
        assertFalse(authorization.canView(SecurityTestIdentity.ORGANIZATION_ID));
    }

    private void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                SecurityTestIdentity.principal(), "test", List.of()));
    }

    private void allowActiveOrganizationAndUser(UUID organizationId) {
        when(organizationContext.requireOrganizationId()).thenReturn(organizationId);
        when(organizationRepository.existsByIdAndArchivedFalse(organizationId)).thenReturn(true);
        when(userRepository.findById(SecurityTestIdentity.USER_ID)).thenReturn(Optional.of(SecurityTestIdentity.user()));
    }

    private OrganizationMembership activeMembership(OrganizationRole role) {
        OrganizationMembership membership = new OrganizationMembership(mock(Organization.class), mock(User.class));
        membership.transitionTo(MembershipStatus.ACTIVE);
        membership.changeRole(role);
        return membership;
    }
}
