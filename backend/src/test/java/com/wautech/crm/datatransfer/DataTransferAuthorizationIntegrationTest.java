package com.wautech.crm.datatransfer;

import com.wautech.crm.company.repository.CompanyRepository;
import com.wautech.crm.company.service.CompanyService;
import com.wautech.crm.contact.repository.ContactRepository;
import com.wautech.crm.contact.service.ContactService;
import com.wautech.crm.identity.entity.User;
import com.wautech.crm.identity.repository.UserRepository;
import com.wautech.crm.lead.repository.LeadRepository;
import com.wautech.crm.lead.service.LeadService;
import com.wautech.crm.opportunity.repository.OpportunityRepository;
import com.wautech.crm.opportunity.service.OpportunityService;
import com.wautech.crm.organization.entity.MembershipStatus;
import com.wautech.crm.organization.entity.Organization;
import com.wautech.crm.organization.entity.OrganizationMembership;
import com.wautech.crm.organization.entity.OrganizationRole;
import com.wautech.crm.organization.repository.OrganizationMembershipRepository;
import com.wautech.crm.organization.repository.OrganizationRepository;
import com.wautech.crm.platform.security.ActiveOrganizationContextFilter;
import com.wautech.crm.platform.security.CrmAuthorization;
import com.wautech.crm.platform.security.SecurityMvcTestSupport;
import com.wautech.crm.platform.security.SessionTenantMvcTestConfiguration;
import com.wautech.crm.platform.test.SecurityTestIdentity;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DataTransferController.class)
@Import({SessionTenantMvcTestConfiguration.class, DataTransferService.class, CsvImportParser.class, CrmAuthorization.class})
class DataTransferAuthorizationIntegrationTest extends SecurityMvcTestSupport {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private CompanyService companyService;
    @MockitoBean private ContactService contactService;
    @MockitoBean private LeadService leadService;
    @MockitoBean private OpportunityService opportunityService;
    @MockitoBean private CompanyRepository companyRepository;
    @MockitoBean private ContactRepository contactRepository;
    @MockitoBean private LeadRepository leadRepository;
    @MockitoBean private OpportunityRepository opportunityRepository;
    @MockitoBean private Validator validator;
    @MockitoBean private OrganizationRepository organizationRepository;
    @MockitoBean private OrganizationMembershipRepository membershipRepository;
    @MockitoBean private UserRepository userRepository;

    @BeforeEach
    void configureActiveMembership() {
        when(organizationRepository.existsByIdAndArchivedFalse(SecurityTestIdentity.ORGANIZATION_ID)).thenReturn(true);
        when(userRepository.findById(SecurityTestIdentity.USER_ID)).thenReturn(Optional.of(SecurityTestIdentity.user()));
        when(validator.validate(any())).thenReturn(Set.of());
        when(companyRepository.findAllByOrganization_IdAndArchivedFalse(eq(SecurityTestIdentity.ORGANIZATION_ID), any()))
                .thenReturn(new PageImpl<>(List.of()));
    }

    @Test
    void ownerAndAdminCanImportButSalesAndViewerCannot() throws Exception {
        setRole(OrganizationRole.OWNER);
        mockMvc.perform(multipart("/api/imports/companies/validate").file(companyFile()).session(session()).with(csrf())
                        .header(ActiveOrganizationContextFilter.ORGANIZATION_HEADER, SecurityTestIdentity.ORGANIZATION_ID.toString()))
                .andExpect(status().isOk());

        setRole(OrganizationRole.ADMIN);
        mockMvc.perform(multipart("/api/imports/companies/validate").file(companyFile()).session(session()).with(csrf())
                        .header(ActiveOrganizationContextFilter.ORGANIZATION_HEADER, SecurityTestIdentity.ORGANIZATION_ID.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.validRows").value(1));

        setRole(OrganizationRole.SALES_USER);
        mockMvc.perform(multipart("/api/imports/companies").file(companyFile()).session(session()).with(csrf())
                        .header(ActiveOrganizationContextFilter.ORGANIZATION_HEADER, SecurityTestIdentity.ORGANIZATION_ID.toString()))
                .andExpect(status().isForbidden());

        setRole(OrganizationRole.VIEWER);
        mockMvc.perform(multipart("/api/imports/companies").file(companyFile()).session(session()).with(csrf())
                        .header(ActiveOrganizationContextFilter.ORGANIZATION_HEADER, SecurityTestIdentity.ORGANIZATION_ID.toString()))
                .andExpect(status().isForbidden());
        verify(companyService, never()).create(any(), any());
    }

    @Test
    void exportIsAvailableToViewerAndCrossTenantSelectionIsRejected() throws Exception {
        setRole(OrganizationRole.VIEWER);
        mockMvc.perform(get("/api/exports/companies").session(session())
                        .header(ActiveOrganizationContextFilter.ORGANIZATION_HEADER, SecurityTestIdentity.ORGANIZATION_ID.toString()))
                .andExpect(status().isOk()).andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("companies-v1.csv")))
                .andExpect(content().contentType("text/csv;charset=UTF-8"));

        mockMvc.perform(get("/api/exports/companies").session(session())
                        .header(ActiveOrganizationContextFilter.ORGANIZATION_HEADER, "10000000-0000-0000-0000-000000000001"))
                .andExpect(status().isForbidden());
        verify(companyRepository, times(1)).findAllByOrganization_IdAndArchivedFalse(eq(SecurityTestIdentity.ORGANIZATION_ID), any());
    }

    @Test
    void rejectsNonCsvFilesAfterImportAuthorizationIsChecked() throws Exception {
        setRole(OrganizationRole.VIEWER);
        MockMultipartFile unsupported = new MockMultipartFile("file", "companies.xlsx", "application/octet-stream", new byte[]{1, 2, 3});
        mockMvc.perform(multipart("/api/imports/companies/validate").file(unsupported).session(session()).with(csrf())
                        .header(ActiveOrganizationContextFilter.ORGANIZATION_HEADER, SecurityTestIdentity.ORGANIZATION_ID.toString()))
                .andExpect(status().isForbidden());

        setRole(OrganizationRole.ADMIN);
        mockMvc.perform(multipart("/api/imports/companies/validate").file(unsupported).session(session()).with(csrf())
                        .header(ActiveOrganizationContextFilter.ORGANIZATION_HEADER, SecurityTestIdentity.ORGANIZATION_ID.toString()))
                .andExpect(status().isBadRequest());
    }

    private void setRole(OrganizationRole role) {
        when(membershipRepository.findByOrganization_IdAndUser_IdAndStatus(
                SecurityTestIdentity.ORGANIZATION_ID, SecurityTestIdentity.USER_ID, MembershipStatus.ACTIVE))
                .thenReturn(Optional.of(membership(role)));
    }

    private OrganizationMembership membership(OrganizationRole role) {
        OrganizationMembership membership = new OrganizationMembership(new Organization("Test"), new User("test@example.test", "Test", "User"));
        membership.transitionTo(MembershipStatus.ACTIVE);
        membership.changeRole(role);
        return membership;
    }

    private static MockMultipartFile companyFile() {
        return new MockMultipartFile("file", "companies.csv", "text/csv",
                "name,website,industry,phone,email,status\nExample,,,,,\n".getBytes(StandardCharsets.UTF_8));
    }

    private static MockHttpSession session() {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                SecurityTestIdentity.principal(), "session", java.util.List.of()));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }
}
