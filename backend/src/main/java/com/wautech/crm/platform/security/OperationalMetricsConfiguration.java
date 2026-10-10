package com.wautech.crm.platform.security;

import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.Meter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;
import java.util.List;

@Configuration(proxyBeanMethods = false)
public class OperationalMetricsConfiguration {
    private static final Set<String> ALLOWED_METRICS = Set.of(
            "http.server.requests", "jvm.memory.used", "jvm.memory.max", "jvm.gc.pause",
            "jvm.threads.live", "process.cpu.usage", "system.cpu.usage",
            "hikaricp.connections", "hikaricp.connections.active", "hikaricp.connections.idle",
            "hikaricp.connections.pending", "hikaricp.connections.max", "hikaricp.connections.min");

    @Bean
    MeterFilter operationalMetricAllowlist() {
        return new MeterFilter() {
            @Override
            public io.micrometer.core.instrument.config.MeterFilterReply accept(Meter.Id id) {
                return ALLOWED_METRICS.contains(id.getName())
                        ? io.micrometer.core.instrument.config.MeterFilterReply.NEUTRAL
                        : io.micrometer.core.instrument.config.MeterFilterReply.DENY;
            }

            @Override
            public Meter.Id map(Meter.Id id) {
                Set<String> permittedTags = ALLOWED_METRICS.contains(id.getName())
                        ? Set.of("uri", "method", "status", "outcome", "exception", "area", "id", "pool")
                        : Set.of();
                List<io.micrometer.core.instrument.Tag> safeTags = id.getTags().stream()
                        .filter(tag -> permittedTags.contains(tag.getKey())).toList();
                return id.replaceTags(safeTags);
            }
        };
    }
}
