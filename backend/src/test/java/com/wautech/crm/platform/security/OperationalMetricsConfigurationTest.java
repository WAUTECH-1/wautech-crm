package com.wautech.crm.platform.security;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OperationalMetricsConfigurationTest {
    @Test
    void exportsOnlyApprovedMetersAndRemovesUnapprovedTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        registry.config().meterFilter(new OperationalMetricsConfiguration().operationalMetricAllowlist());

        registry.counter("http.server.requests", "uri", "/api/companies/{id}",
                "method", "GET", "organizationId", "private-org", "customerEmail", "private@example.test")
                .increment();
        registry.counter("crm.customer.lookup", "userId", "private-user").increment();

        assertThat(registry.find("http.server.requests").counter()).isNotNull();
        assertThat(registry.find("http.server.requests").meter().getId().getTags().stream()
                .map(io.micrometer.core.instrument.Tag::getKey).toList())
                .containsExactlyInAnyOrder("uri", "method");
        assertThat(registry.find("crm.customer.lookup").counter()).isNull();
    }
}
