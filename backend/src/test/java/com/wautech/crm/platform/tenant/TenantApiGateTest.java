package com.wautech.crm.platform.tenant;

import com.wautech.crm.company.controller.CompanyController;
import com.wautech.crm.company.service.CompanyService;
import com.wautech.crm.platform.security.SecurityConfiguration;
import com.wautech.crm.platform.security.SecurityMvcTestSupport;
import com.wautech.crm.platform.test.SecurityTestIdentity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CompanyController.class)
@Import({AuthenticatedOrganizationContext.class, SecurityConfiguration.class})
class TenantApiGateTest extends SecurityMvcTestSupport {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private CompanyService companyService;

    @Test
    void tenantApiRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/companies"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(companyService);
    }

    @Test
    void authenticatedTenantApiRequiresActiveOrganizationMembership() throws Exception {
        mockMvc.perform(get("/api/companies").with(user(SecurityTestIdentity.principal())))
                .andExpect(status().isForbidden());

        verifyNoInteractions(companyService);
    }

    @Test
    void anOrganizationHeaderDoesNotGrantAccessWithoutActiveMembership() throws Exception {
        when(membershipService.isActiveMember(SecurityTestIdentity.ORGANIZATION_ID, SecurityTestIdentity.USER_ID))
                .thenReturn(false);

        mockMvc.perform(get("/api/companies").with(user(SecurityTestIdentity.principal()))
                        .header("X-Organization-ID", SecurityTestIdentity.ORGANIZATION_ID))
                .andExpect(status().isForbidden());

        verifyNoInteractions(companyService);
    }

    @Test
    void archivedOrganizationCannotBeSelectedAsActiveTenant() throws Exception {
        when(membershipService.isActiveMember(SecurityTestIdentity.ORGANIZATION_ID, SecurityTestIdentity.USER_ID))
                .thenThrow(new com.wautech.crm.organization.service.OrganizationNotFoundException(
                        SecurityTestIdentity.ORGANIZATION_ID));

        mockMvc.perform(get("/api/companies").with(user(SecurityTestIdentity.principal()))
                        .header("X-Organization-ID", SecurityTestIdentity.ORGANIZATION_ID))
                .andExpect(status().isForbidden());

        verifyNoInteractions(companyService);
    }
}
