package com.wautech.crm.identity.controller;
import com.wautech.crm.identity.service.CurrentUserProfileService;
import com.wautech.crm.identity.dto.UserResponse;
import com.wautech.crm.platform.security.*;
import com.wautech.crm.platform.test.SecurityTestIdentity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(CurrentUserProfileController.class)
@Import(SecurityConfiguration.class)
class CurrentUserProfileControllerTest extends SecurityMvcTestSupport {
    @Autowired MockMvc mvc;
    @MockitoBean CurrentUserProfileService profiles;
    @Test void rejectsAnonymousMissingCsrfAndInvalidNames() throws Exception {
        mvc.perform(get("/api/auth/profile")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/auth/profile").with(user(SecurityTestIdentity.principal())).contentType(MediaType.APPLICATION_JSON).content("{\"firstName\":\"A\",\"lastName\":\"B\"}")).andExpect(status().isForbidden());
        mvc.perform(patch("/api/auth/profile").with(user(SecurityTestIdentity.principal())).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"firstName\":\" \",\"lastName\":\"B\"}")).andExpect(status().isBadRequest());
        verifyNoInteractions(profiles);
    }
    @Test void readsWithoutTenantHeaderAndPreventsCaching() throws Exception {
        when(profiles.get()).thenReturn(UserResponse.from(SecurityTestIdentity.user()));
        mvc.perform(get("/api/auth/profile").with(user(SecurityTestIdentity.principal()))).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.passwordHash").doesNotExist());
    }
}
