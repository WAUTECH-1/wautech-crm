package com.wautech.localsetup;

import com.wautech.crm.identity.entity.User;
import com.wautech.crm.identity.repository.UserRepository;
import com.wautech.crm.identity.service.CredentialProvisioningService;
import com.wautech.crm.identity.service.UserService;
import com.wautech.crm.organization.entity.*;
import com.wautech.crm.organization.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LocalAccountSetupTest {
    private final UUID organizationId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UserRepository users = mock(UserRepository.class);
    private final OrganizationRepository organizations = mock(OrganizationRepository.class);
    private final OrganizationMembershipRepository memberships = mock(OrganizationMembershipRepository.class);
    private final UserService userService = mock(UserService.class);
    private final CredentialProvisioningService credentials = mock(CredentialProvisioningService.class);
    private final Organization organization = new Organization("Local development");
    private final User user = new User(LocalAccountSetup.EMAIL, "Local", "Administrator");
    private final LocalAccountSetup.PasswordInput input = mock(LocalAccountSetup.PasswordInput.class);

    private void prepare() {
        ReflectionTestUtils.setField(user, "id", userId);
        when(organizations.lockByIdAndArchivedFalse(organizationId)).thenReturn(Optional.of(organization));
        when(users.findByEmail(LocalAccountSetup.EMAIL)).thenReturn(Optional.of(user));
        when(memberships.findAllByOrganization_IdAndUser_IdOrderByCreatedAtDesc(organizationId, userId)).thenReturn(List.of());
    }

    private void run() {
        LocalAccountSetup.provision(organizationId, users, organizations, memberships, userService, credentials, input);
    }

    @Test void provisionsMissingAccountAndActiveAdminMembership() {
        prepare();
        when(users.findByEmail(LocalAccountSetup.EMAIL)).thenReturn(Optional.empty());
        when(userService.create(any())).thenReturn(com.wautech.crm.identity.dto.UserResponse.from(user));
        when(userService.requireUser(userId)).thenReturn(user);
        when(input.read()).thenReturn("test-only-password");
        run();
        verify(credentials).provisionInitialPassword(userId, "test-only-password");
        verify(memberships).save(argThat(m -> m.getOrganization() == organization && m.getUser() == user
                && m.getStatus() == MembershipStatus.ACTIVE && m.getRole() == OrganizationRole.ADMIN));
    }

    @Test void rerunDoesNotReadOrResetPasswordOrDuplicateMembership() {
        prepare();
        ReflectionTestUtils.setField(user, "passwordHash", "existing-hash");
        var membership = new OrganizationMembership(organization, user);
        membership.transitionTo(MembershipStatus.ACTIVE);
        membership.changeRole(OrganizationRole.ADMIN);
        when(memberships.findAllByOrganization_IdAndUser_IdOrderByCreatedAtDesc(organizationId, userId)).thenReturn(List.of(membership));
        run();
        verifyNoInteractions(input, credentials, userService);
        verify(memberships, never()).save(any());
    }

    @Test void refusesConflictingMembershipBeforeProvisioningPassword() {
        prepare();
        when(memberships.findAllByOrganization_IdAndUser_IdOrderByCreatedAtDesc(organizationId, userId))
                .thenReturn(List.of(new OrganizationMembership(organization, user)));
        assertThrows(IllegalStateException.class, this::run);
        verifyNoInteractions(input, credentials);
        verify(memberships, never()).save(any());
    }

    @Test void refusesDisabledUser() {
        prepare();
        user.setEnabled(false);
        assertThrows(IllegalStateException.class, this::run);
        verifyNoInteractions(input, credentials);
    }

    @Test void refusesMissingOrArchivedOrganization() {
        assertThrows(IllegalStateException.class, this::run);
        verifyNoInteractions(users, input, credentials);
    }

    @ParameterizedTest
    @ValueSource(strings = {"jdbc:postgresql://production:5432/crm", "jdbc:postgresql://localhost:5432/crm?sslmode=disable", "jdbc:postgresql://localhost/crm", "jdbc:postgresql://localhost:5432/crm/extra"})
    void refusesNonExplicitLocalTargets(String url) {
        assertThrows(IllegalArgumentException.class, () -> LocalAccountSetup.validateTarget(url, url, "local-account-setup"));
    }

    @Test void requiresExactConfirmationAndExclusiveProfile() {
        String url = "jdbc:postgresql://127.0.0.1:5432/wautech_crm";
        assertDoesNotThrow(() -> LocalAccountSetup.validateTarget(url, url, "local-account-setup"));
        assertThrows(IllegalArgumentException.class, () -> LocalAccountSetup.validateTarget(url, null, "local-account-setup"));
        assertThrows(IllegalArgumentException.class, () -> LocalAccountSetup.validateTarget(url, url, "production"));
        assertThrows(IllegalArgumentException.class, () -> LocalAccountSetup.validateTarget(url, url, "local-account-setup,production"));
    }
}
