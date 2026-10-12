package com.wautech.crm.organization.service;
import com.wautech.crm.identity.service.UserService;
import com.wautech.crm.identity.service.DisabledUserException;
import com.wautech.crm.organization.entity.*;
import com.wautech.crm.organization.repository.OrganizationMembershipRepository;
import com.wautech.crm.platform.test.SecurityTestIdentity;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class CurrentUserOrganizationServiceTest {
    final OrganizationMembershipRepository repository = mock(OrganizationMembershipRepository.class);
    final UserService users = mock(UserService.class);
    final CurrentUserOrganizationService service = new CurrentUserOrganizationService(repository, users);
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    void login() {
        var principal = SecurityTestIdentity.principal();
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal,null,principal.getAuthorities()));
        when(users.requireEnabledUser(SecurityTestIdentity.USER_ID)).thenReturn(SecurityTestIdentity.user());
    }
    @Test void anonymousAndUnexpectedPrincipalsCannotQueryMemberships() {
        assertThrows(AuthenticationCredentialsNotFoundException.class, service::list);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated("unexpected",null,List.of()));
        assertThrows(AuthenticationCredentialsNotFoundException.class, service::list);
        verifyNoInteractions(repository);
    }
    @Test void queryIsScopedToPersistedEnabledPrincipalAndActiveNonArchivedMemberships() {
        login();
        var organization = new Organization("Fixture");
        ReflectionTestUtils.setField(organization,"id",SecurityTestIdentity.ORGANIZATION_ID);
        var membership = new OrganizationMembership(organization,SecurityTestIdentity.user());
        ReflectionTestUtils.setField(membership,"id",UUID.randomUUID());
        membership.transitionTo(MembershipStatus.ACTIVE); membership.changeRole(OrganizationRole.VIEWER);
        when(repository.findAllByUser_IdAndStatusAndOrganization_ArchivedFalseOrderByOrganization_NameAsc(SecurityTestIdentity.USER_ID,MembershipStatus.ACTIVE)).thenReturn(List.of(membership));
        var result = service.list();
        assertEquals(1,result.size()); assertEquals(OrganizationRole.VIEWER,result.getFirst().role());
        assertEquals(SecurityTestIdentity.ORGANIZATION_ID,result.getFirst().organizationId());
        verify(users).requireEnabledUser(SecurityTestIdentity.USER_ID);
        verify(repository).findAllByUser_IdAndStatusAndOrganization_ArchivedFalseOrderByOrganization_NameAsc(SecurityTestIdentity.USER_ID,MembershipStatus.ACTIVE);
    }
    @Test void disabledPrincipalCannotReadEvenIfSessionWasPreviouslyValid() {
        login(); when(users.requireEnabledUser(SecurityTestIdentity.USER_ID)).thenThrow(new DisabledUserException(SecurityTestIdentity.USER_ID));
        assertThrows(DisabledUserException.class,service::list); verifyNoInteractions(repository);
    }
    @Test void foreignOrUnavailableOrganizationReturnsSameDenial() {
        login(); when(repository.findAllByUser_IdAndStatusAndOrganization_ArchivedFalseOrderByOrganization_NameAsc(SecurityTestIdentity.USER_ID,MembershipStatus.ACTIVE)).thenReturn(List.of());
        assertThrows(AccessDeniedException.class,()->service.membership(UUID.randomUUID()));
    }
}
