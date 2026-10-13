package com.wautech.crm.identity.controller;
import com.wautech.crm.organization.dto.CurrentUserOrganizationResponse;
import com.wautech.crm.organization.entity.OrganizationRole;
import com.wautech.crm.organization.service.CurrentUserOrganizationService;
import com.wautech.crm.platform.security.SecurityConfiguration;
import com.wautech.crm.platform.security.SecurityMvcTestSupport;
import com.wautech.crm.platform.test.SecurityTestIdentity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(CurrentUserOrganizationController.class)
@Import(SecurityConfiguration.class)
class CurrentUserOrganizationControllerTest extends SecurityMvcTestSupport {
    @Autowired MockMvc mvc;
    @MockitoBean CurrentUserOrganizationService discovery;
    @Test void anonymousCannotDiscover() throws Exception {
        mvc.perform(get("/api/auth/organizations")).andExpect(status().isUnauthorized());
        verifyNoInteractions(discovery);
    }
    @Test void sessionCanDiscoverWithoutTenantHeaderAndCannotChooseAnotherUser() throws Exception {
        when(discovery.list()).thenReturn(List.of(new CurrentUserOrganizationResponse(
                SecurityTestIdentity.ORGANIZATION_ID, "Test organization", UUID.randomUUID(), OrganizationRole.VIEWER, "ACTIVE")));
        mvc.perform(get("/api/auth/organizations").with(user(SecurityTestIdentity.principal()))
                .param("userId", UUID.randomUUID().toString()))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$[0].role").value("VIEWER"))
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());
    }
    @Test void inaccessibleMembershipReturnsGeneric403() throws Exception {
        UUID id = UUID.randomUUID();
        when(discovery.membership(id)).thenThrow(new AccessDeniedException("Organization access is not permitted"));
        mvc.perform(get("/api/auth/organizations/" + id + "/membership").with(user(SecurityTestIdentity.principal())))
                .andExpect(status().isForbidden());
    }
    @Test void invalidUuidReturns400() throws Exception {
        mvc.perform(get("/api/auth/organizations/invalid/membership").with(user(SecurityTestIdentity.principal())))
                .andExpect(status().isBadRequest());
    }
}
