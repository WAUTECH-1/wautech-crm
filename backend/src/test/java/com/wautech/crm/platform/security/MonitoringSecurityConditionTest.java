package com.wautech.crm.platform.security;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MonitoringSecurityConditionTest {
    private final MonitoringSecurityCondition condition = new MonitoringSecurityCondition();

    @Test
    void disablesMonitoringWhenDisabledOrTrustConfigurationIsIncomplete() {
        assertThat(matches(new MockEnvironment())).isFalse();
        assertThat(matches(new MockEnvironment().withProperty("management.security.monitoring.enabled", "true")
                .withProperty("management.security.monitoring.issuer-uri", "https://issuer.example/")))
                .isFalse();
    }

    @Test
    void enablesMonitoringOnlyWithHttpsTrustEndpointsAndAudience() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("management.security.monitoring.enabled", "true")
                .withProperty("management.security.monitoring.issuer-uri", "https://issuer.example/")
                .withProperty("management.security.monitoring.jwk-set-uri", "https://issuer.example/jwks")
                .withProperty("management.security.monitoring.audience", "crm-metrics");
        assertThat(matches(environment)).isTrue();

        environment.setProperty("management.security.monitoring.jwk-set-uri", "http://issuer.example/jwks");
        assertThat(matches(environment)).isFalse();
    }

    private boolean matches(MockEnvironment environment) {
        ConditionContext context = mock(ConditionContext.class);
        when(context.getEnvironment()).thenReturn(environment);
        return condition.matches(context, null);
    }
}
