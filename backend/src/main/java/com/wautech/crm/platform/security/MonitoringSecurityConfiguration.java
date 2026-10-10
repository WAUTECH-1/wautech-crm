package com.wautech.crm.platform.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import java.util.List;
import java.util.Objects;

/** A separate stateless chain for one machine-only scrape endpoint. */
@Configuration(proxyBeanMethods = false)
@Conditional(MonitoringSecurityCondition.class)
@EnableConfigurationProperties(MonitoringSecurityProperties.class)
public class MonitoringSecurityConfiguration {
    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    JwtDecoder monitoringJwtDecoder(MonitoringSecurityProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri()).build();
        OAuth2TokenValidator<Jwt> issuerAndTime = JwtValidators.createDefaultWithIssuer(properties.issuerUri());
        OAuth2TokenValidator<Jwt> audience = new JwtClaimValidator<List<String>>("aud",
                values -> values != null && values.contains(properties.audience()));
        OAuth2TokenValidator<Jwt> expirationRequired = new JwtClaimValidator<>("exp", Objects::nonNull);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuerAndTime, audience, expirationRequired));
        return decoder;
    }

    @Bean
    @Order(1)
    SecurityFilterChain monitoringSecurityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder)
            throws Exception {
        http.securityMatcher(PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/actuator/prometheus"))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable())
                .requestCache(cache -> cache.disable())
                .authorizeHttpRequests(authorize -> authorize.anyRequest().hasAuthority("SCOPE_metrics.read"))
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(jwt -> jwt.decoder(jwtDecoder)));
        return http.build();
    }
}
