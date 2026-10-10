package com.wautech.crm.platform.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "management.security.monitoring")
public record MonitoringSecurityProperties(String issuerUri, String jwkSetUri, String audience) { }
