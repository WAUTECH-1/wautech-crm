package com.wautech.crm.identity.controller;

import com.wautech.crm.audit.entity.AuditOutcome;
import com.wautech.crm.audit.service.AuditEventWriter;
import com.wautech.crm.identity.dto.AuthenticatedUserResponse;
import com.wautech.crm.identity.dto.CsrfTokenResponse;
import com.wautech.crm.identity.dto.LoginRequest;
import com.wautech.crm.identity.security.CrmUserPrincipal;
import com.wautech.crm.identity.service.AuthenticationService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/auth")
public class AuthenticationController {
    private final AuthenticationService authenticationService;
    private final ObjectProvider<AuditEventWriter> auditEventWriterProvider;
    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

    public AuthenticationController(AuthenticationService authenticationService,
            ObjectProvider<AuditEventWriter> auditEventWriterProvider) {
        this.authenticationService = authenticationService;
        this.auditEventWriterProvider = auditEventWriterProvider;
        logoutHandler.setInvalidateHttpSession(true);
        logoutHandler.setClearAuthentication(true);
    }

    @GetMapping("/csrf")
    public CsrfTokenResponse csrf(CsrfToken csrfToken) {
        return new CsrfTokenResponse(csrfToken.getHeaderName(), csrfToken.getParameterName(), csrfToken.getToken());
    }

    @PostMapping("/login")
    public AuthenticatedUserResponse login(@Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
        return authenticationService.login(request, servletRequest, servletResponse);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        if (org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication()
                .getPrincipal() instanceof CrmUserPrincipal principal) {
            AuditEventWriter auditEventWriter = auditEventWriterProvider.getIfAvailable();
            if (auditEventWriter != null) {
                auditEventWriter.recordSecurity(null, principal.getId(), "LOGOUT_SUCCEEDED", "USER", principal.getId(),
                        AuditOutcome.SUCCESS, java.util.Map.of());
            }
        }
        logoutHandler.logout(request, response, org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication());
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @GetMapping("/me")
    public AuthenticatedUserResponse me(@AuthenticationPrincipal CrmUserPrincipal principal) {
        return AuthenticatedUserResponse.from(principal);
    }
}
