package com.wautech.crm.organization.controller;
import com.wautech.crm.organization.entity.*;
import com.wautech.crm.organization.repository.*;
import com.wautech.crm.organization.service.*;
import com.wautech.crm.identity.repository.UserRepository;
import com.wautech.crm.identity.service.UserService;
import com.wautech.crm.audit.service.AuditEventWriter;
import com.wautech.crm.notification.service.NotificationEventWriter;
import com.wautech.crm.platform.security.*;
import com.wautech.crm.platform.test.SecurityTestIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real controller, tenant filter, method security and organization/membership services. */
@WebMvcTest(OrganizationAdministrationController.class)
@Import({SessionTenantMvcTestConfiguration.class, OrganizationService.class, OrganizationMembershipService.class, CrmAuthorization.class})
class OrganizationAdministrationControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean OrganizationRepository organizations;
    @MockitoBean OrganizationMembershipRepository memberships;
    @MockitoBean UserRepository users;
    @MockitoBean UserService userService;
    @MockitoBean UserDetailsService userDetailsService;
    @MockitoBean AuditEventWriter auditEventWriter;
    @MockitoBean NotificationEventWriter notificationEventWriter;
    final UUID org = SecurityTestIdentity.ORGANIZATION_ID;
    final UUID memberId = UUID.randomUUID();
    Organization organization;
    @BeforeEach void setup() {
        organization = mock(Organization.class);
        when(organization.getId()).thenReturn(org);
        when(organization.getName()).thenReturn("Test organization");
        when(organizations.findByIdAndArchivedFalse(org)).thenReturn(Optional.of(organization));
        when(organizations.lockByIdAndArchivedFalse(org)).thenReturn(Optional.of(organization));
        when(organizations.existsByIdAndArchivedFalse(org)).thenReturn(true);
        when(users.findById(SecurityTestIdentity.USER_ID)).thenReturn(Optional.of(SecurityTestIdentity.user()));
        when(userService.requireUser(SecurityTestIdentity.USER_ID)).thenReturn(SecurityTestIdentity.user());
        when(userService.requireEnabledUser(SecurityTestIdentity.USER_ID)).thenReturn(SecurityTestIdentity.user());
        role(OrganizationRole.ADMIN);
    }
    void role(OrganizationRole role) {
        var actor = new OrganizationMembership(organization, SecurityTestIdentity.user());
        actor.transitionTo(MembershipStatus.ACTIVE); actor.changeRole(role);
        when(memberships.findByOrganization_IdAndUser_IdAndStatus(org, SecurityTestIdentity.USER_ID, MembershipStatus.ACTIVE)).thenReturn(Optional.of(actor));
        when(memberships.existsByOrganization_IdAndUser_IdAndStatus(org, SecurityTestIdentity.USER_ID, MembershipStatus.ACTIVE)).thenReturn(true);
    }
    @Test void adminReadsOnlyCurrentOrganization() throws Exception {
        when(memberships.findAllByOrganization_IdOrderByCreatedAtDesc(org)).thenReturn(List.of());
        mvc.perform(get("/api/organization/memberships").with(user(SecurityTestIdentity.principal())).header("X-Organization-ID", org)).andExpect(status().isOk()).andExpect(content().json("[]"));
        verify(memberships).findAllByOrganization_IdOrderByCreatedAtDesc(org);
        mvc.perform(get("/api/organization").with(user(SecurityTestIdentity.principal())).header("X-Organization-ID", org)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(org.toString()));
    }
    @Test void viewerAndSalesUserCannotReadAdministrationOrWrite() throws Exception {
        for (var role : List.of(OrganizationRole.VIEWER, OrganizationRole.SALES_USER)) {
            role(role);
            mvc.perform(get("/api/organization/memberships").with(user(SecurityTestIdentity.principal())).header("X-Organization-ID", org)).andExpect(status().isForbidden());
            mvc.perform(patch("/api/organization").with(user(SecurityTestIdentity.principal())).with(csrf()).header("X-Organization-ID", org).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Denied\"}")).andExpect(status().isForbidden());
        }
        verify(memberships, never()).findAllByOrganization_IdOrderByCreatedAtDesc(any());
        verify(organizations, never()).save(any());
    }
    @Test void anonymousForeignTenantAndMissingCsrfAreRejected() throws Exception {
        mvc.perform(get("/api/organization")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/organization").with(user(SecurityTestIdentity.principal())).header("X-Organization-ID", UUID.randomUUID())).andExpect(status().isForbidden());
        mvc.perform(patch("/api/organization").with(user(SecurityTestIdentity.principal())).header("X-Organization-ID", org).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Denied\"}")).andExpect(status().isForbidden());
        verify(organizations, never()).save(any());
    }
    @Test void validatesDtosAndRejectsForeignMembershipIds() throws Exception {
        mvc.perform(post("/api/organization/memberships").with(user(SecurityTestIdentity.principal())).with(csrf()).header("X-Organization-ID", org).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/organization").with(user(SecurityTestIdentity.principal())).with(csrf()).header("X-Organization-ID", org).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" \"}")).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/organization/memberships/" + memberId + "/role").with(user(SecurityTestIdentity.principal())).with(csrf()).header("X-Organization-ID", org).contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"VIEWER\"}")).andExpect(status().isNotFound());
        verify(memberships).findByIdAndOrganization_Id(memberId, org);
        verify(memberships, never()).save(any());
    }
    @Test void membershipCreateRoleAndActivationUseExistingServices() throws Exception {
        UUID targetId = UUID.randomUUID();
        var target = mock(com.wautech.crm.identity.entity.User.class);
        when(target.getId()).thenReturn(targetId);
        when(userService.requireUser(targetId)).thenReturn(target);
        when(userService.requireEnabledUser(targetId)).thenReturn(target);
        when(memberships.save(any())).thenAnswer(call -> call.getArgument(0));
        mvc.perform(post("/api/organization/memberships").with(user(SecurityTestIdentity.principal())).with(csrf()).header("X-Organization-ID", org).contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\"" + targetId + "\"}")).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("INVITED")).andExpect(jsonPath("$.role").value("VIEWER"));
        var membership = new OrganizationMembership(organization, target);
        when(memberships.findByIdAndOrganization_Id(memberId, org)).thenReturn(Optional.of(membership));
        mvc.perform(patch("/api/organization/memberships/" + memberId + "/role").with(user(SecurityTestIdentity.principal())).with(csrf()).header("X-Organization-ID", org).contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"SALES_USER\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("SALES_USER"));
        mvc.perform(patch("/api/organization/memberships/" + memberId + "/status").with(user(SecurityTestIdentity.principal())).with(csrf()).header("X-Organization-ID", org).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACTIVE\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
        mvc.perform(patch("/api/organization/memberships/" + memberId + "/status").with(user(SecurityTestIdentity.principal())).with(csrf()).header("X-Organization-ID", org).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SUSPENDED\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUSPENDED"));
    }
}
