package com.wautech.crm.platform.security;

import com.wautech.crm.audit.service.AuditEventWriter;
import com.wautech.crm.identity.repository.UserRepository;
import com.wautech.crm.identity.service.UserService;
import com.wautech.crm.organization.entity.MembershipStatus;
import com.wautech.crm.organization.entity.Organization;
import com.wautech.crm.organization.entity.OrganizationMembership;
import com.wautech.crm.organization.entity.OrganizationRole;
import com.wautech.crm.organization.repository.OrganizationMembershipRepository;
import com.wautech.crm.organization.repository.OrganizationRepository;
import com.wautech.crm.organization.service.OrganizationMembershipService;
import com.wautech.crm.platform.health.DatabaseReadiness;
import com.wautech.crm.platform.health.OperationalDiagnosticsController;
import com.wautech.crm.platform.tenant.AuthenticatedOrganizationContext;
import com.wautech.crm.platform.test.SecurityTestIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Exercises real session, tenant, role checks on admin diagnostics and actuator routes. */
@WebMvcTest(OperationalDiagnosticsController.class)
@Import({SecurityConfiguration.class, CrmAuthorization.class, AuthenticatedOrganizationContext.class,
        OperationalAuthorizationIntegrationTest.MetricsProbeConfiguration.class})
class OperationalAuthorizationIntegrationTest {
    private OrganizationRole roleForCurrentTest = OrganizationRole.ADMIN;
    @Autowired private MockMvc mockMvc;
    @MockitoBean private AuditEventWriter auditEventWriter;
    @MockitoBean private UserService userService;
    @MockitoBean private OrganizationMembershipService membershipService;
    @MockitoBean private UserDetailsService userDetailsService;
    @MockitoBean private UserRepository userRepository;
    @MockitoBean private OrganizationRepository organizationRepository;
    @MockitoBean private OrganizationMembershipRepository membershipRepository;
    @MockitoBean private DatabaseReadiness databaseReadiness;

    @BeforeEach
    void commonSetup() {
        when(userService.requireEnabledUser(SecurityTestIdentity.USER_ID)).thenReturn(SecurityTestIdentity.user());
        when(membershipService.isActiveMember(SecurityTestIdentity.ORGANIZATION_ID, SecurityTestIdentity.USER_ID))
                .thenReturn(true);
        when(organizationRepository.existsByIdAndArchivedFalse(SecurityTestIdentity.ORGANIZATION_ID)).thenReturn(true);
        when(userRepository.findById(SecurityTestIdentity.USER_ID)).thenReturn(Optional.of(SecurityTestIdentity.user()));
        when(membershipRepository.findByOrganization_IdAndUser_IdAndStatus(SecurityTestIdentity.ORGANIZATION_ID,
                SecurityTestIdentity.USER_ID, MembershipStatus.ACTIVE)).thenAnswer(invocation ->
                Optional.of(membership(roleForCurrentTest)));
        when(databaseReadiness.isDatabaseReady()).thenReturn(true);
    }

    @Test
    void unauthenticatedDiagnosticsAreRejected() throws Exception {
        mockMvc.perform(get("/api/ops/health").header("X-Organization-ID", SecurityTestIdentity.ORGANIZATION_ID))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/test-metrics")
                        .header("X-Organization-ID", SecurityTestIdentity.ORGANIZATION_ID.toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void viewerCannotReadAdministrativeDiagnosticsOrMetrics() throws Exception {
        roleForCurrentTest = OrganizationRole.VIEWER;
        mockMvc.perform(get("/api/ops/health").with(user(SecurityTestIdentity.principal()))
                        .header("X-Organization-ID", SecurityTestIdentity.ORGANIZATION_ID.toString()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/actuator/test-metrics").with(user(SecurityTestIdentity.principal()))
                        .header("X-Organization-ID", SecurityTestIdentity.ORGANIZATION_ID.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminReadsSanitizedDiagnosticsAndMetricsWithinActiveOrganization() throws Exception {
        roleForCurrentTest = OrganizationRole.ADMIN;
        mockMvc.perform(get("/api/ops/health").with(user(SecurityTestIdentity.principal()))
                        .header("X-Organization-ID", SecurityTestIdentity.ORGANIZATION_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"UP\",\"database\":\"UP\"}"));
        mockMvc.perform(get("/actuator/test-metrics").with(user(SecurityTestIdentity.principal()))
                        .header("X-Organization-ID", SecurityTestIdentity.ORGANIZATION_ID.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string("metrics"));
    }

    @Test
    void invalidOrganizationSelectionCannotReachDiagnostics() throws Exception {
        mockMvc.perform(get("/actuator/test-metrics").with(user(SecurityTestIdentity.principal()))
                        .header("X-Organization-ID", "10000000-0000-0000-0000-000000000001"))
                .andExpect(status().isForbidden());
    }

    private static OrganizationMembership membership(OrganizationRole role) {
        OrganizationMembership membership = new OrganizationMembership(new Organization("Test Organization"),
                SecurityTestIdentity.user());
        membership.transitionTo(MembershipStatus.ACTIVE);
        membership.changeRole(role);
        return membership;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MetricsProbeConfiguration {
        @Bean MetricsProbe metricsProbe() { return new MetricsProbe(); }
    }

    @RestController
    static class MetricsProbe {
        @GetMapping("/actuator/test-metrics")
        String metrics() { return "metrics"; }
    }
}
