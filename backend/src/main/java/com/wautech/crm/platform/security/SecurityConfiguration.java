package com.wautech.crm.platform.security;

import com.wautech.crm.audit.entity.AuditOutcome;
import com.wautech.crm.audit.service.AuditEventWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wautech.crm.identity.security.UnprovisionedPasswordHash;
import com.wautech.crm.identity.security.CrmUserPrincipal;
import com.wautech.crm.identity.service.UserService;
import com.wautech.crm.organization.service.OrganizationMembershipService;
import com.wautech.crm.platform.tenant.AuthenticatedOrganizationContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfiguration {
    @Bean
    PasswordEncoder passwordEncoder(@Value("${app.security.bcrypt-strength:12}") int strength) {
        if (strength < 10 || strength > 16) {
            throw new IllegalStateException("app.security.bcrypt-strength must be between 10 and 16");
        }
        return new BCryptPasswordEncoder(strength);
    }

    @Bean
    UnprovisionedPasswordHash unprovisionedPasswordHash(PasswordEncoder passwordEncoder, SecureRandom secureRandom) {
        byte[] random = new byte[32];
        secureRandom.nextBytes(random);
        String impossibleCredential = Base64.getEncoder().encodeToString(random);
        return new UnprovisionedPasswordHash(passwordEncoder.encode(impossibleCredential));
    }

    @Bean
    SecureRandom secureRandom() { return new SecureRandom(); }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    SecurityContextRepository securityContextRepository() { return new HttpSessionSecurityContextRepository(); }

    @Bean
    SessionAuthenticationStrategy sessionAuthenticationStrategy() { return new ChangeSessionIdAuthenticationStrategy(); }

    @Bean
    AuthenticationEntryPoint apiAuthenticationEntryPoint(ObjectMapper objectMapper) {
        return (request, response, exception) -> SecurityProblemWriter.write(response, objectMapper,
                HttpStatus.UNAUTHORIZED, "Authentication is required");
    }

    @Bean
    AccessDeniedHandler apiAccessDeniedHandler(ObjectMapper objectMapper, AuditEventWriter auditEventWriter) {
        return (request, response, exception) -> {
            var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            UUID actorId = authentication != null && authentication.getPrincipal() instanceof CrmUserPrincipal principal
                    ? principal.getId() : null;
            Object organizationAttribute = request.getAttribute(AuthenticatedOrganizationContext.REQUEST_ATTRIBUTE);
            UUID organizationId = organizationAttribute instanceof UUID id ? id : null;
            try {
                auditEventWriter.recordSecurity(organizationId, actorId, "ACCESS_DENIED", "API_REQUEST", null,
                        AuditOutcome.FAILURE, java.util.Map.of("method", request.getMethod()));
            } catch (RuntimeException auditFailure) {
                // Preserve the generic security response if the audit store itself is unavailable.
            }
            SecurityProblemWriter.write(response, objectMapper, HttpStatus.FORBIDDEN, "Request is not permitted");
        };
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, UserService userService,
            OrganizationMembershipService membershipService, ObjectMapper objectMapper,
            AuditEventWriter auditEventWriter,
            ObjectProvider<CrmAuthorization> crmAuthorizationProvider,
            SecurityContextRepository securityContextRepository, AuthenticationEntryPoint apiAuthenticationEntryPoint,
            AccessDeniedHandler apiAccessDeniedHandler) throws Exception {
        HttpSessionCsrfTokenRepository csrfRepository = new HttpSessionCsrfTokenRepository();
        csrfRepository.setHeaderName("X-CSRF-TOKEN");

        http
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.changeSessionId()))
                .csrf(csrf -> csrf.csrfTokenRepository(csrfRepository)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .requestCache(cache -> cache.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/api/health", "/api/health/liveness", "/api/health/readiness",
                                "/actuator/health/liveness", "/actuator/health/readiness", "/api/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers("/api/auth/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/actuator/**").access(operationalAdminAccess(crmAuthorizationProvider))
                        .requestMatchers("/actuator/**").denyAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(apiAuthenticationEntryPoint)
                        .accessDeniedHandler(apiAccessDeniedHandler))
                .addFilterAfter(new UserEnabledSessionFilter(userService, apiAuthenticationEntryPoint),
                        org.springframework.security.web.context.SecurityContextHolderFilter.class)
                .addFilterAfter(new ActiveOrganizationContextFilter(membershipService, objectMapper, auditEventWriter),
                        UserEnabledSessionFilter.class)
                .addFilterBefore(new CorrelationIdFilter(),
                        org.springframework.security.web.context.SecurityContextHolderFilter.class);

        return http.build();
    }

    private AuthorizationManager<RequestAuthorizationContext> operationalAdminAccess(
            ObjectProvider<CrmAuthorization> crmAuthorizationProvider) {
        return (authentication, context) -> {
            Object organization = context.getRequest().getAttribute(AuthenticatedOrganizationContext.REQUEST_ATTRIBUTE);
            CrmAuthorization crmAuthorization = crmAuthorizationProvider.getIfAvailable();
            boolean permitted = crmAuthorization != null && organization instanceof UUID organizationId
                    && crmAuthorization.canManageOrganization(organizationId);
            return new AuthorizationDecision(permitted);
        };
    }
}
