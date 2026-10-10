package com.wautech.crm.notification.controller;

import com.wautech.crm.identity.entity.User;
import com.wautech.crm.identity.repository.UserRepository;
import com.wautech.crm.notification.repository.NotificationRepository;
import com.wautech.crm.notification.entity.Notification;
import com.wautech.crm.notification.entity.NotificationType;
import com.wautech.crm.notification.service.NotificationService;
import com.wautech.crm.organization.entity.MembershipStatus;
import com.wautech.crm.organization.entity.Organization;
import com.wautech.crm.organization.entity.OrganizationMembership;
import com.wautech.crm.organization.entity.OrganizationRole;
import com.wautech.crm.organization.repository.OrganizationMembershipRepository;
import com.wautech.crm.organization.repository.OrganizationRepository;
import com.wautech.crm.organization.service.OrganizationService;
import com.wautech.crm.platform.security.ActiveOrganizationContextFilter;
import com.wautech.crm.platform.security.CrmAuthorization;
import com.wautech.crm.platform.security.SecurityMvcTestSupport;
import com.wautech.crm.platform.security.SessionTenantMvcTestConfiguration;
import com.wautech.crm.platform.test.SecurityTestIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.mockito.stubbing.OngoingStubbing;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NotificationController.class)
@Import({SessionTenantMvcTestConfiguration.class, NotificationService.class, CrmAuthorization.class})
class NotificationAuthorizationIntegrationTest extends SecurityMvcTestSupport {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private NotificationRepository notificationRepository;
    @MockitoBean private OrganizationService organizationService;
    @MockitoBean private OrganizationRepository organizationRepository;
    @MockitoBean private OrganizationMembershipRepository organizationMembershipRepository;
    @MockitoBean private UserRepository userRepository;

    @BeforeEach
    void configureActiveViewer() {
        when(organizationRepository.existsByIdAndArchivedFalse(SecurityTestIdentity.ORGANIZATION_ID)).thenReturn(true);
        when(userRepository.findById(SecurityTestIdentity.USER_ID))
                .thenReturn(Optional.of(SecurityTestIdentity.user()));
        when(organizationMembershipRepository.findByOrganization_IdAndUser_IdAndStatus(
                SecurityTestIdentity.ORGANIZATION_ID, SecurityTestIdentity.USER_ID, MembershipStatus.ACTIVE))
                .thenReturn(Optional.of(membership()));
        when(organizationService.requireActiveOrganization(SecurityTestIdentity.ORGANIZATION_ID))
                .thenReturn(new Organization("Test Organization"));
        when(notificationRepository.findAllByOrganizationIdAndRecipientUserId(
                eq(SecurityTestIdentity.ORGANIZATION_ID), eq(SecurityTestIdentity.USER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
    }

    @Test
    void unauthenticatedCannotListNotifications() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(notificationRepository);
    }

    @Test
    void activeViewerCanReadOwnPageInTrustedOrganization() throws Exception {
        mockMvc.perform(get("/api/notifications").session(authenticatedSession())
                        .header(ActiveOrganizationContextFilter.ORGANIZATION_HEADER,
                                SecurityTestIdentity.ORGANIZATION_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));

        verify(notificationRepository).findAllByOrganizationIdAndRecipientUserId(
                eq(SecurityTestIdentity.ORGANIZATION_ID), eq(SecurityTestIdentity.USER_ID), any(Pageable.class));
    }

    @Test
    void cannotSupplyAnotherOrganizationToReadNotifications() throws Exception {
        mockMvc.perform(get("/api/notifications").session(authenticatedSession())
                        .header(ActiveOrganizationContextFilter.ORGANIZATION_HEADER,
                                "10000000-0000-0000-0000-000000000001"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(notificationRepository);
    }

    @Test
    void invalidPageIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/notifications").param("page", "-1")
                        .session(authenticatedSession())
                        .header(ActiveOrganizationContextFilter.ORGANIZATION_HEADER,
                                SecurityTestIdentity.ORGANIZATION_ID.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unreadCountAndReadAllUseCurrentRecipientScope() throws Exception {
        when(notificationRepository.countByOrganizationIdAndRecipientUserIdAndReadAtIsNull(
                SecurityTestIdentity.ORGANIZATION_ID, SecurityTestIdentity.USER_ID)).thenReturn(2L);
        when(notificationRepository.markAllRead(eq(SecurityTestIdentity.ORGANIZATION_ID),
                eq(SecurityTestIdentity.USER_ID), any())).thenReturn(2);

        mockMvc.perform(get("/api/notifications/unread-count").session(authenticatedSession())
                        .header(ActiveOrganizationContextFilter.ORGANIZATION_HEADER,
                                SecurityTestIdentity.ORGANIZATION_ID.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.count").value(2));
        mockMvc.perform(patch("/api/notifications/read-all").session(authenticatedSession())
                        .header(ActiveOrganizationContextFilter.ORGANIZATION_HEADER,
                                SecurityTestIdentity.ORGANIZATION_ID.toString()).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.updatedCount").value(2));

        verify(notificationRepository).countByOrganizationIdAndRecipientUserIdAndReadAtIsNull(
                SecurityTestIdentity.ORGANIZATION_ID, SecurityTestIdentity.USER_ID);
        verify(notificationRepository).markAllRead(eq(SecurityTestIdentity.ORGANIZATION_ID),
                eq(SecurityTestIdentity.USER_ID), any());
    }

    @Test
    void unavailableNotificationReturnsNotFoundAndReadUsesCurrentRecipientScope() throws Exception {
        UUID notificationId = UUID.randomUUID();
        OngoingStubbing<Optional<Notification>> lookup = when(notificationRepository.findByIdAndOrganizationIdAndRecipientUserId(
                notificationId, SecurityTestIdentity.ORGANIZATION_ID, SecurityTestIdentity.USER_ID))
                .thenReturn(Optional.empty());
        lookup.thenReturn(Optional.of(new Notification(SecurityTestIdentity.ORGANIZATION_ID,
                        SecurityTestIdentity.USER_ID, NotificationType.ORGANIZATION_MEMBERSHIP_ACTIVATED,
                        "Access active", "Your access is active.", null, null, null)));
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(call -> (Notification) call.getArgument(0));

        mockMvc.perform(get("/api/notifications/{id}", notificationId).session(authenticatedSession())
                        .header(ActiveOrganizationContextFilter.ORGANIZATION_HEADER,
                                SecurityTestIdentity.ORGANIZATION_ID.toString()))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/notifications/{id}/read", notificationId).session(authenticatedSession())
                        .header(ActiveOrganizationContextFilter.ORGANIZATION_HEADER,
                                SecurityTestIdentity.ORGANIZATION_ID.toString()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("ORGANIZATION_MEMBERSHIP_ACTIVATED"))
                .andExpect(jsonPath("$.readAt").exists());
    }

    private OrganizationMembership membership() {
        Organization organization = new Organization("Test Organization");
        User user = SecurityTestIdentity.user();
        OrganizationMembership membership = new OrganizationMembership(organization, user);
        membership.transitionTo(MembershipStatus.ACTIVE);
        membership.changeRole(OrganizationRole.VIEWER);
        return membership;
    }

    private MockHttpSession authenticatedSession() {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                SecurityTestIdentity.principal(), "session", List.of()));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }
}
