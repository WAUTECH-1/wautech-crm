package com.wautech.crm.organization.service;

import com.wautech.crm.identity.entity.User;
import com.wautech.crm.identity.service.DisabledUserException;
import com.wautech.crm.identity.service.UserService;
import com.wautech.crm.organization.entity.IllegalMembershipTransitionException;
import com.wautech.crm.organization.entity.MembershipStatus;
import com.wautech.crm.organization.entity.Organization;
import com.wautech.crm.organization.entity.OrganizationMembership;
import com.wautech.crm.organization.repository.OrganizationMembershipRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrganizationMembershipServiceTest {
    @Mock private OrganizationMembershipRepository membershipRepository;
    @Mock private OrganizationService organizationService;
    @Mock private UserService userService;
    @InjectMocks private OrganizationMembershipService service;

    private final UUID organizationId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private Organization organization;
    private User user;

    @BeforeEach
    void setUp() {
        organization = mock(Organization.class);
        user = mock(User.class);
    }

    @Test
    void createsInvitedMembershipAndRejectsDuplicate() {
        stubEntityIds();
        when(organizationService.requireActiveOrganization(organizationId)).thenReturn(organization);
        when(userService.requireUser(userId)).thenReturn(user);
        when(membershipRepository.existsByOrganization_IdAndUser_Id(organizationId, userId)).thenReturn(false, true);
        when(membershipRepository.save(any(OrganizationMembership.class))).thenAnswer(call -> call.getArgument(0));

        var response = service.create(organizationId, userId);

        assertEquals(MembershipStatus.INVITED, response.status());
        assertEquals(organizationId, response.organizationId());
        assertThrows(DuplicateOrganizationMembershipException.class, () -> service.create(organizationId, userId));
    }

    @Test
    void supportsAllowedTransitionsAndChecksActiveMembership() {
        stubEntityIds();
        when(user.isEnabled()).thenReturn(true);
        when(userService.requireUser(userId)).thenReturn(user);
        when(userService.requireEnabledUser(userId)).thenReturn(user);
        OrganizationMembership membership = new OrganizationMembership(organization, user);
        prepareMembership(membership);

        assertEquals(MembershipStatus.ACTIVE, service.transition(organizationId, UUID.randomUUID(), MembershipStatus.ACTIVE).status());
        assertTrue(service.isActiveMember(organizationId, userId));
        assertEquals(MembershipStatus.SUSPENDED, service.transition(organizationId, UUID.randomUUID(), MembershipStatus.SUSPENDED).status());
        assertFalse(service.isActiveMember(organizationId, userId));
        assertEquals(MembershipStatus.ACTIVE, service.transition(organizationId, UUID.randomUUID(), MembershipStatus.ACTIVE).status());
        assertEquals(MembershipStatus.REVOKED, service.transition(organizationId, UUID.randomUUID(), MembershipStatus.REVOKED).status());
        assertFalse(service.isActiveMember(organizationId, userId));
    }

    @Test
    void allowsInvitedRevocationAndRejectsIllegalTransitions() {
        stubUserId();
        OrganizationMembership membership = new OrganizationMembership(organization, user);
        prepareMembership(membership);
        when(userService.requireEnabledUser(userId)).thenReturn(user);
        service.transition(organizationId, UUID.randomUUID(), MembershipStatus.REVOKED);
        assertThrows(IllegalMembershipTransitionException.class,
                () -> service.transition(organizationId, UUID.randomUUID(), MembershipStatus.ACTIVE));
    }

    @Test
    void disabledUserCannotBeActivatedOrCountAsActiveMember() {
        stubUserId();
        OrganizationMembership membership = new OrganizationMembership(organization, user);
        prepareMembership(membership);
        when(userService.requireEnabledUser(userId)).thenThrow(new DisabledUserException(userId));
        assertThrows(DisabledUserException.class,
                () -> service.transition(organizationId, UUID.randomUUID(), MembershipStatus.ACTIVE));
        verify(membershipRepository, never()).save(any());
        when(userService.requireUser(userId)).thenReturn(user);
        when(user.isEnabled()).thenReturn(false);
        assertFalse(service.isActiveMember(organizationId, userId));
    }

    @Test
    void archivedOrganizationCannotReceiveMembershipOrTransition() {
        when(organizationService.requireActiveOrganization(organizationId))
                .thenThrow(new OrganizationNotFoundException(organizationId));
        assertThrows(OrganizationNotFoundException.class, () -> service.create(organizationId, userId));
        assertThrows(OrganizationNotFoundException.class,
                () -> service.transition(organizationId, UUID.randomUUID(), MembershipStatus.ACTIVE));
        verifyNoInteractions(membershipRepository);
    }

    @Test
    void membershipLookupIsOrganizationScoped() {
        UUID membershipId = UUID.randomUUID();
        when(organizationService.requireActiveOrganization(organizationId)).thenReturn(organization);
        when(membershipRepository.findByIdAndOrganization_Id(membershipId, organizationId)).thenReturn(Optional.empty());
        assertThrows(OrganizationMembershipNotFoundException.class, () -> service.get(organizationId, membershipId));
        verify(membershipRepository).findByIdAndOrganization_Id(membershipId, organizationId);
        verify(membershipRepository, never()).findById(membershipId);
    }

    @Test
    void listForUserIsScopedToTheRequestedOrganization() {
        when(organizationService.requireActiveOrganization(organizationId)).thenReturn(organization);
        when(userService.requireUser(userId)).thenReturn(user);
        service.listByUser(organizationId, userId);
        verify(membershipRepository).findAllByOrganization_IdAndUser_IdOrderByCreatedAtDesc(organizationId, userId);
    }

    private void prepareMembership(OrganizationMembership membership) {
        when(organizationService.requireActiveOrganization(organizationId)).thenReturn(organization);
        when(membershipRepository.findByIdAndOrganization_Id(any(), eq(organizationId))).thenReturn(Optional.of(membership));
        lenient().when(membershipRepository.save(membership)).thenReturn(membership);
        lenient().when(membershipRepository.existsByOrganization_IdAndUser_IdAndStatus(organizationId, userId, MembershipStatus.ACTIVE))
                .thenAnswer(call -> membership.getStatus() == MembershipStatus.ACTIVE && user.isEnabled());
    }

    private void stubEntityIds() {
        when(organization.getId()).thenReturn(organizationId);
        stubUserId();
    }

    private void stubUserId() {
        when(user.getId()).thenReturn(userId);
    }
}
