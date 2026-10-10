package com.wautech.crm.platform.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import com.wautech.crm.audit.service.AuditEventWriter;
import com.wautech.crm.identity.service.UserService;
import com.wautech.crm.identity.repository.UserRepository;
import com.wautech.crm.organization.service.OrganizationMembershipService;
import com.wautech.crm.organization.repository.OrganizationRepository;
import com.wautech.crm.organization.repository.OrganizationMembershipRepository;
import com.wautech.crm.platform.health.DatabaseReadiness;
import com.wautech.crm.platform.health.OperationalDiagnosticsController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OperationalDiagnosticsController.class)
@Import({SecurityConfiguration.class, MonitoringSecurityConfiguration.class, CrmAuthorization.class,
        com.wautech.crm.platform.tenant.AuthenticatedOrganizationContext.class,
        MonitoringAuthorizationIntegrationTest.ProbeConfiguration.class,
        MonitoringAuthorizationIntegrationTest.JwtTestConfiguration.class})
@TestPropertySource(properties = {
        "management.security.monitoring.enabled=true",
        "management.security.monitoring.issuer-uri=https://issuer.test.example/",
        "management.security.monitoring.jwk-set-uri=https://issuer.test.example/jwks",
        "management.security.monitoring.audience=wautech-crm-metrics"})
class MonitoringAuthorizationIntegrationTest {
    private static final String ISSUER = "https://issuer.test.example/";
    private static final String AUDIENCE = "wautech-crm-metrics";
    private static final String SCOPE = "metrics.read";
    private static final KeyPair TRUSTED = keyPair();
    private static final KeyPair UNTRUSTED = keyPair();

    @Autowired MockMvc mockMvc;
    @Autowired JwtEncoder jwtEncoder;
    @MockitoBean AuditEventWriter auditEventWriter;
    @MockitoBean UserService userService;
    @MockitoBean OrganizationMembershipService membershipService;
    @MockitoBean UserDetailsService userDetailsService;
    @MockitoBean UserRepository userRepository;
    @MockitoBean OrganizationRepository organizationRepository;
    @MockitoBean OrganizationMembershipRepository membershipRepository;
    @MockitoBean DatabaseReadiness databaseReadiness;

    @Test void acceptsValidSignedTokenWithIssuerAudienceAndScope() throws Exception {
        mockMvc.perform(get("/actuator/prometheus").header("Authorization", bearer(token(TRUSTED, ISSUER, AUDIENCE, SCOPE,
                        Instant.now().plusSeconds(120)))))
                .andExpect(status().isOk());
    }

    @Test void rejectsMissingToken() throws Exception {
        mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
    }

    @Test void rejectsInvalidSignature() throws Exception {
        mockMvc.perform(get("/actuator/prometheus").header("Authorization", bearer(token(UNTRUSTED, ISSUER, AUDIENCE,
                        SCOPE, Instant.now().plusSeconds(120)))))
                .andExpect(status().isUnauthorized());
    }

    @Test void rejectsExpiredToken() throws Exception {
        mockMvc.perform(get("/actuator/prometheus").header("Authorization", bearer(token(TRUSTED, ISSUER, AUDIENCE,
                        SCOPE, Instant.now().minusSeconds(300)))))
                .andExpect(status().isUnauthorized());
    }

    @Test void rejectsTokenWithoutExpirationClaim() throws Exception {
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(ISSUER).subject("metrics-scraper")
                .audience(List.of(AUDIENCE)).issuedAt(Instant.now().minusSeconds(2)).claim("scope", SCOPE).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).getTokenValue();
        mockMvc.perform(get("/actuator/prometheus").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    @Test void rejectsWrongIssuerAudienceAndScope() throws Exception {
        mockMvc.perform(get("/actuator/prometheus").header("Authorization", bearer(token(TRUSTED,
                        "https://other.test.example/", AUDIENCE, SCOPE, Instant.now().plusSeconds(120)))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/prometheus").header("Authorization", bearer(token(TRUSTED,
                        ISSUER, "other-audience", SCOPE, Instant.now().plusSeconds(120)))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/prometheus").header("Authorization", bearer(token(TRUSTED,
                        ISSUER, AUDIENCE, "metrics.write", Instant.now().plusSeconds(120)))))
                .andExpect(status().isForbidden());
    }

    @Test void monitoringTokenCannotAccessCrmOrOtherActuatorEndpoints() throws Exception {
        String authorization = bearer(token(TRUSTED, ISSUER, AUDIENCE, SCOPE, Instant.now().plusSeconds(120)));
        mockMvc.perform(get("/api/private-test").header("Authorization", authorization)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/metrics").header("Authorization", authorization)).andExpect(status().isUnauthorized());
    }

    private String token(KeyPair signingKey, String issuer, String audience, String scope, Instant expiresAt) {
        Instant issuedAt = expiresAt.isBefore(Instant.now()) ? expiresAt.minusSeconds(60) : Instant.now().minusSeconds(2);
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(issuer).subject("metrics-scraper")
                .audience(List.of(audience)).issuedAt(issuedAt).expiresAt(expiresAt)
                .claim("scope", scope).id(UUID.randomUUID().toString()).build();
        JwtEncoder encoder = signingKey == TRUSTED ? jwtEncoder : encoder(signingKey);
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims))
                .getTokenValue();
    }

    private static JwtEncoder encoder(KeyPair keyPair) {
        RSAKey jwk = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic()).privateKey(keyPair.getPrivate()).build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(jwk)));
    }

    private static String bearer(String token) { return "Bearer " + token; }

    private static KeyPair keyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class JwtTestConfiguration {
        @Bean @Primary JwtDecoder testMonitoringJwtDecoder() {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) TRUSTED.getPublic()).build();
            decoder.setJwtValidator(new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(
                    JwtValidators.createDefaultWithIssuer(ISSUER),
                    new JwtClaimValidator<List<String>>("aud", values -> values != null && values.contains(AUDIENCE)),
                    new JwtClaimValidator<Object>("exp", java.util.Objects::nonNull)));
            return decoder;
        }
        @Bean JwtEncoder jwtEncoder() { return encoder(TRUSTED); }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProbeConfiguration {
        @Bean MetricsProbe metricsProbe() { return new MetricsProbe(); }
    }
    @RestController static class MetricsProbe {
        @GetMapping("/actuator/prometheus") String metrics() { return "metrics"; }
    }
}
