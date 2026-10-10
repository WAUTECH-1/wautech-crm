package com.wautech.crm.platform.security;

import com.wautech.crm.company.controller.CompanyController;
import com.wautech.crm.company.repository.CompanyRepository;
import com.wautech.crm.company.service.CompanyService;
import com.wautech.crm.identity.entity.User;
import com.wautech.crm.identity.repository.UserRepository;
import com.wautech.crm.organization.entity.MembershipStatus;
import com.wautech.crm.organization.entity.Organization;
import com.wautech.crm.organization.entity.OrganizationMembership;
import com.wautech.crm.organization.entity.OrganizationRole;
import com.wautech.crm.organization.repository.OrganizationMembershipRepository;
import com.wautech.crm.organization.repository.OrganizationRepository;
import com.wautech.crm.organization.service.OrganizationService;
import com.wautech.crm.platform.security.SecurityMvcTestSupport;
import com.wautech.crm.platform.test.SecurityTestIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Exercises Spring session/tenant filters and the real service method-security policy together. */
@WebMvcTest(CompanyController.class)
@Import({SessionTenantMvcTestConfiguration.class, CompanyService.class, CrmAuthorization.class})
class CompanyAuthorizationIntegrationTest extends SecurityMvcTestSupport {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private CompanyRepository companyRepository;
    @MockitoBean private OrganizationService organizationService;
    @MockitoBean private OrganizationRepository organizationRepository;
    @MockitoBean private OrganizationMembershipRepository membershipRepository;
    @MockitoBean private UserRepository userRepository;

    @BeforeEach
    void configureViewerMembership() {
        when(organizationRepository.existsByIdAndArchivedFalse(SecurityTestIdentity.ORGANIZATION_ID)).thenReturn(true);
        when(userRepository.findById(SecurityTestIdentity.USER_ID)).thenReturn(Optional.of(SecurityTestIdentity.user()));
        when(membershipRepository.findByOrganization_IdAndUser_IdAndStatus(
                SecurityTestIdentity.ORGANIZATION_ID, SecurityTestIdentity.USER_ID, MembershipStatus.ACTIVE))
                .thenReturn(Optional.of(membership(OrganizationRole.VIEWER)));
        when(organizationService.requireActiveOrganization(SecurityTestIdentity.ORGANIZATION_ID))
                .thenReturn(new Organization("Test Organization"));
        when(companyRepository.findAllByOrganization_IdAndArchivedFalseOrderByCreatedAtDesc(
                SecurityTestIdentity.ORGANIZATION_ID)).thenReturn(List.of());
    }

    @Test
    void viewerCanReadThroughAuthenticatedTenantContextAndRealServiceAuthorization() throws Exception {
        mockMvc.perform(get("/api/companies").session(authenticatedSession())
                        .header(ActiveOrganizationContextFilter.ORGANIZATION_HEADER,
                                SecurityTestIdentity.ORGANIZATION_ID.toString()))
                .andExpect(status().isOk());

        verify(companyRepository).findAllByOrganization_IdAndArchivedFalseOrderByCreatedAtDesc(
                SecurityTestIdentity.ORGANIZATION_ID);
    }

    @Test
    void viewerCannotWriteAndDeniedCallNeverReachesCompanyPersistence() throws Exception {
        mockMvc.perform(post("/api/companies")
                        .session(authenticatedSession())
                        .header(ActiveOrganizationContextFilter.ORGANIZATION_HEADER,
                                SecurityTestIdentity.ORGANIZATION_ID.toString())
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Denied Co\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(companyRepository);
    }

    @Test
    void crossOrganizationSelectionIsRejectedBeforeTheServiceRuns() throws Exception {
        mockMvc.perform(get("/api/companies").session(authenticatedSession()).header(ActiveOrganizationContextFilter.ORGANIZATION_HEADER,
                        "10000000-0000-0000-0000-000000000001"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(companyRepository);
    }

    private OrganizationMembership membership(OrganizationRole role) {
        OrganizationMembership membership = new OrganizationMembership(new Organization("Test Organization"),
                SecurityTestIdentity.user());
        membership.transitionTo(MembershipStatus.ACTIVE);
        membership.changeRole(role);
        return membership;
    }

    private MockHttpSession authenticatedSession() {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                SecurityTestIdentity.principal(), "session", java.util.List.of()));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }
}
