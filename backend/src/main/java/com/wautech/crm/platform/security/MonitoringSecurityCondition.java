package com.wautech.crm.platform.security;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.StringUtils;

import java.net.URI;

/** Enables the isolated monitoring chain only when all trust settings are present. */
public final class MonitoringSecurityCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Environment environment = context.getEnvironment();
        return environment.getProperty("management.security.monitoring.enabled", Boolean.class, false)
                && isHttpsUri(environment.getProperty("management.security.monitoring.issuer-uri"))
                && isHttpsUri(environment.getProperty("management.security.monitoring.jwk-set-uri"))
                && StringUtils.hasText(environment.getProperty("management.security.monitoring.audience"));
    }

    private boolean isHttpsUri(String value) {
        if (!StringUtils.hasText(value)) return false;
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && StringUtils.hasText(uri.getHost());
        } catch (IllegalArgumentException invalidUri) {
            return false;
        }
    }
}
