package com.wautech.crm.platform.tenant;

import com.wautech.crm.company.controller.CompanyController;
import com.wautech.crm.company.service.CompanyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CompanyController.class)
@Import(AuthenticatedOrganizationContext.class)
class TenantApiGateTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private CompanyService companyService;

    @Test
    void tenantApiIsUnavailableWithoutServerEstablishedOrganizationContext() throws Exception {
        mockMvc.perform(get("/api/companies"))
                .andExpect(status().isServiceUnavailable());

        verifyNoInteractions(companyService);
    }
}
