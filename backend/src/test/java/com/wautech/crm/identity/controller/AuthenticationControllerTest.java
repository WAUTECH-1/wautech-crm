package com.wautech.crm.identity.controller;

import com.wautech.crm.audit.entity.AuditOutcome;
import com.wautech.crm.identity.security.CrmUserPrincipal;
import com.wautech.crm.identity.service.DisabledUserException;
import com.wautech.crm.identity.service.AuthenticationService;
import com.wautech.crm.platform.security.SecurityConfiguration;
import com.wautech.crm.platform.security.SecurityMvcTestSupport;
import com.wautech.crm.platform.test.SecurityTestIdentity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthenticationController.class)
@Import({SecurityConfiguration.class, AuthenticationService.class})
class AuthenticationControllerTest extends SecurityMvcTestSupport {
    @Autowired private MockMvc mockMvc;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void csrfBootstrapReturnsAHeaderTokenAndLoginRejectsMissingCsrf() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andReturn();
        assertFalse(result.getResponse().getContentAsString().contains("password"));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("Good-password-123")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(userDetailsService);
    }

    @Test
    void loginEstablishesSessionAndRotatesTheSessionId() throws Exception {
        String rawPassword = "Good-password-123";
        CrmUserPrincipal principal = principal(passwordEncoder.encode(rawPassword), true);
        when(userDetailsService.loadUserByUsername(SecurityTestIdentity.EMAIL)).thenReturn(principal);

        MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        String token = com.jayway.jsonpath.JsonPath.read(csrfResult.getResponse().getContentAsString(), "$.token");
        MockHttpSession session = (MockHttpSession) csrfResult.getRequest().getSession(false);
        assertNotNull(session);
        String originalSessionId = session.getId();

        MvcResult login = mockMvc.perform(post("/api/auth/login").session(session)
                        .header("X-CSRF-TOKEN", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(rawPassword)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(SecurityTestIdentity.USER_ID.toString()))
                .andExpect(jsonPath("$.email").value(SecurityTestIdentity.EMAIL))
                .andReturn();

        assertNotEquals(originalSessionId, session.getId());
        assertNotNull(login.getRequest().getSession(false)
                .getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY));
        verify(auditEventWriter).recordSecurity(isNull(), eq(SecurityTestIdentity.USER_ID), eq("LOGIN_SUCCEEDED"),
                eq("USER"), eq(SecurityTestIdentity.USER_ID), eq(AuditOutcome.SUCCESS), eq(java.util.Map.of()));
    }

    @Test
    void incorrectPasswordAndUnknownUserHaveTheSameSafeResponse() throws Exception {
        when(userDetailsService.loadUserByUsername(SecurityTestIdentity.EMAIL))
                .thenReturn(principal(passwordEncoder.encode("Good-password-123"), true));
        mockMvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("Wrong-password-123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));

        reset(userDetailsService);
        when(userDetailsService.loadUserByUsername(SecurityTestIdentity.EMAIL))
                .thenThrow(new UsernameNotFoundException("missing"));
        mockMvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("Good-password-123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
        verify(auditEventWriter, times(2)).recordSecurity(isNull(), isNull(), eq("LOGIN_FAILED"), eq("USER"),
                isNull(), eq(AuditOutcome.FAILURE), eq(java.util.Map.of()));
    }

    @Test
    void loginRejectsPasswordsThatExceedBcryptByteLimit() throws Exception {
        mockMvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("é".repeat(37))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
        verifyNoInteractions(userDetailsService);
    }

    @Test
    void loginDoesNotAuthenticateDisabledAccounts() throws Exception {
        CrmUserPrincipal disabled = principal(passwordEncoder.encode("Good-password-123"), false);
        when(userDetailsService.loadUserByUsername(SecurityTestIdentity.EMAIL)).thenReturn(disabled);
        mockMvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("Good-password-123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
        assertFalse(disabled.isEnabled());
    }

    @Test
    void meUsesTheAuthenticatedPrincipalAndIgnoresClientUserIds() throws Exception {
        mockMvc.perform(get("/api/auth/me").with(user(SecurityTestIdentity.principal()))
                        .param("userId", java.util.UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(SecurityTestIdentity.USER_ID.toString()))
                .andExpect(jsonPath("$.email").value(SecurityTestIdentity.EMAIL))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void logoutInvalidatesTheAuthenticatedSession() throws Exception {
        CrmUserPrincipal principal = SecurityTestIdentity.principal();
        var securityContext = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);

        mockMvc.perform(post("/api/auth/logout").session(session).with(csrf()))
                .andExpect(status().isNoContent());

        assertTrue(session.isInvalid());
        verify(auditEventWriter).recordSecurity(isNull(), eq(SecurityTestIdentity.USER_ID), eq("LOGOUT_SUCCEEDED"),
                eq("USER"), eq(SecurityTestIdentity.USER_ID), eq(AuditOutcome.SUCCESS), eq(java.util.Map.of()));
    }

    @Test
    void disablingAUserInvalidatesTheirExistingSession() throws Exception {
        CrmUserPrincipal principal = SecurityTestIdentity.principal();
        var securityContext = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);
        when(userService.requireEnabledUser(SecurityTestIdentity.USER_ID))
                .thenThrow(new DisabledUserException(SecurityTestIdentity.USER_ID));

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isUnauthorized());

        assertTrue(session.isInvalid());
    }

    private CrmUserPrincipal principal(String hash, boolean enabled) {
        return new CrmUserPrincipal(SecurityTestIdentity.USER_ID, SecurityTestIdentity.EMAIL,
                "Test", "User", hash, enabled);
    }

    private String loginJson(String password) {
        return "{\"email\":\"" + SecurityTestIdentity.EMAIL + "\",\"password\":\"" + password + "\"}";
    }
}
