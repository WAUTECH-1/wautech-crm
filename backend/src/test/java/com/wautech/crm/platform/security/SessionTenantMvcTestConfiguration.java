package com.wautech.crm.platform.security;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import com.wautech.crm.platform.tenant.AuthenticatedOrganizationContext;

@TestConfiguration(proxyBeanMethods = false)
@Import({SecurityConfiguration.class, AuthenticatedOrganizationContext.class})
public class SessionTenantMvcTestConfiguration {
}
