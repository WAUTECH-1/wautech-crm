package com.wautech.crm.platform.security;

import com.wautech.crm.audit.entity.AuditOutcome;
import com.wautech.crm.audit.service.AuditEventWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wautech.crm.identity.security.CrmUserPrincipal;
import com.wautech.crm.identity.service.DisabledUserException;
import com.wautech.crm.identity.service.UserNotFoundException;
import com.wautech.crm.organization.service.OrganizationMembershipService;
import com.wautech.crm.organization.service.OrganizationNotFoundException;
import com.wautech.crm.platform.tenant.AuthenticatedOrganizationContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/** Validates the requested organization on every CRM API call before setting the trusted request attribute. */
public class ActiveOrganizationContextFilter extends OncePerRequestFilter {
    public static final String ORGANIZATION_HEADER = "X-Organization-ID";

    private final OrganizationMembershipService membershipService;
    private final ObjectMapper objectMapper;
    private final AuditEventWriter auditEventWriter;

    public ActiveOrganizationContextFilter(OrganizationMembershipService membershipService, ObjectMapper objectMapper,
            AuditEventWriter auditEventWriter) {
        this.membershipService = membershipService;
        this.objectMapper = objectMapper;
        this.auditEventWriter = auditEventWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.equals("/api/health") || path.startsWith("/api/health/") || path.startsWith("/api/auth/")) {
            return true;
        }
        if (path.equals("/actuator/health/liveness") || path.equals("/actuator/health/readiness")) {
            return true;
        }
        return !path.startsWith("/api/") && !path.startsWith("/actuator/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof org.springframework.security.authentication.AnonymousAuthenticationToken) {
            chain.doFilter(request, response);
            return;
        }

        String requestedOrganization = request.getHeader(ORGANIZATION_HEADER);
        if (requestedOrganization == null || requestedOrganization.isBlank()) {
            recordDenied(request, authentication);
            SecurityProblemWriter.write(response, objectMapper, HttpStatus.FORBIDDEN,
                    "An active organization membership is required");
            return;
        }
        UUID organizationId;
        try {
            organizationId = UUID.fromString(requestedOrganization);
        } catch (IllegalArgumentException exception) {
            SecurityProblemWriter.write(response, objectMapper, HttpStatus.BAD_REQUEST,
                    "Organization identifier is invalid");
            return;
        }

        if (!(authentication.getPrincipal() instanceof CrmUserPrincipal principal)) {
            SecurityProblemWriter.write(response, objectMapper, HttpStatus.UNAUTHORIZED, "Authentication is required");
            return;
        }
        boolean activeMember;
        try {
            activeMember = membershipService.isActiveMember(organizationId, principal.getId());
        } catch (OrganizationNotFoundException | UserNotFoundException | DisabledUserException exception) {
            activeMember = false;
        }
        if (!activeMember) {
            recordDenied(request, authentication);
            SecurityProblemWriter.write(response, objectMapper, HttpStatus.FORBIDDEN,
                    "An active organization membership is required");
            return;
        }

        request.setAttribute(AuthenticatedOrganizationContext.REQUEST_ATTRIBUTE, organizationId);
        chain.doFilter(request, response);
    }

    private void recordDenied(HttpServletRequest request, Authentication authentication) {
        UUID actorId = authentication.getPrincipal() instanceof CrmUserPrincipal principal ? principal.getId() : null;
        try {
            // The supplied tenant ID has not been authenticated; keep the event platform-level.
            auditEventWriter.recordSecurity(null, actorId, "ACCESS_DENIED", "API_REQUEST", null,
                    AuditOutcome.FAILURE, java.util.Map.of("method", request.getMethod()));
        } catch (RuntimeException auditFailure) {
            // The authorization response must remain generic and stable if storage is unavailable.
        }
    }
}
