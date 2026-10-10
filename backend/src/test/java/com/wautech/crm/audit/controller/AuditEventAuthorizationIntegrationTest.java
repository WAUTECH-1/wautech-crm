package com.wautech.crm.audit.controller;

import com.wautech.crm.audit.entity.AuditOutcome;
import com.wautech.crm.audit.repository.AuditEventRepository;
import com.wautech.crm.audit.service.AuditQueryService;
import com.wautech.crm.identity.entity.User;
import com.wautech.crm.identity.repository.UserRepository;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Verifies the audit endpoint against persisted organization roles and tenant checks. */
@WebMvcTest(AuditEventController.class)
@Import({SessionTenantMvcTestConfiguration.class, AuditQueryService.class, CrmAuthorization.class})
class AuditEventAuthorizationIntegrationTest extends SecurityMvcTestSupport {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private AuditEventRepository auditEventRepository;
    @MockitoBean private OrganizationService organizationService;
    @MockitoBean private OrganizationRepository organizationRepository;
    @MockitoBean private OrganizationMembershipRepository membershipRepository;
    @MockitoBean private UserRepository userRepository;

    @BeforeEach
    void setUpActiveViewer() {
        when(organizationRepository.existsByIdAndArchivedFalse(SecurityTestIdentity.ORGANIZATION_ID)).thenReturn(true);
        when(userRepository.findById(SecurityTestIdentity.USER_ID)).thenReturn(Optional.of(SecurityTestIdentity.user()));
        when(membershipRepository.findByOrganization_IdAndUser_IdAndStatus(
                SecurityTestIdentity.ORGANIZATION_ID, SecurityTestIdentity.USER_ID, MembershipStatus.ACTIVE))
                .thenReturn(Optional.of(membership(OrganizationRole.VIEWER)));
        when(organizationService.requireActiveOrganization(SecurityTestIdentity.ORGANIZATION_ID))
                .thenReturn(new Organization("Test Organization"));
        when(auditEventRepository.findOrganizationEvents(eq(SecurityTestIdentity.ORGANIZATION_ID), any(), any(),
                any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 50), 0));
    }

    @Test
    void viewerCannotInspectAuditEvents() throws Exception {
        mockMvc.perform(get("/api/audit-events")
                        .session(authenticatedSession())
                        .header(ActiveOrganizationContextFilter.ORGANIZATION_HEADER,
                                SecurityTestIdentity.ORGANIZATION_ID.toString()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(auditEventRepository);
    }

    @Test
    void activeAdminCanReadOnlyOwnOrganizationEvents() throws Exception {
        when(membershipRepository.findByOrganization_IdAndUser_IdAndStatus(
                SecurityTestIdentity.ORGANIZATION_ID, SecurityTestIdentity.USER_ID, MembershipStatus.ACTIVE))
                .thenReturn(Optional.of(membership(OrganizationRole.ADMIN)));

        mockMvc.perform(get("/api/audit-events")
                        .session(authenticatedSession())
                        .header(ActiveOrganizationContextFilter.ORGANIZATION_HEADER,
                                SecurityTestIdentity.ORGANIZATION_ID.toString()))
                .andExpect(status().isOk());

        verify(auditEventRepository).findOrganizationEvents(eq(SecurityTestIdentity.ORGANIZATION_ID), isNull(),
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any());
    }

    @Test
    void cannotSelectAnotherOrganizationThroughTenantHeader() throws Exception {
        mockMvc.perform(get("/api/audit-events")
                        .session(authenticatedSession())
                        .header(ActiveOrganizationContextFilter.ORGANIZATION_HEADER,
                                java.util.UUID.randomUUID().toString()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(auditEventRepository);
    }

    private OrganizationMembership membership(OrganizationRole role) {
        OrganizationMembership membership = new OrganizationMembership(new Organization("Test Organization"),
                SecurityTestIdentity.user());
        membership.transitionTo(MembershipStatus.ACTIVE);
        membership.changeRole(role);
        return membership;
    }

    private org.springframework.mock.web.MockHttpSession authenticatedSession() {
        var context = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        context.setAuthentication(org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(
                SecurityTestIdentity.principal(), "session", java.util.List.of()));
        var session = new org.springframework.mock.web.MockHttpSession();
        session.setAttribute(org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                context);
        return session;
    }
}
