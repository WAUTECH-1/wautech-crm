package com.wautech.crm.platform.security;

import com.wautech.crm.identity.security.CrmUserPrincipal;
import com.wautech.crm.platform.test.SecurityTestIdentity;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@TestConfiguration(proxyBeanMethods = false)
@Import(SecurityConfiguration.class)
public class TenantMvcTestConfiguration {
    @Bean
    MockMvcBuilderCustomizer authenticatedTenantDefaults() {
        CrmUserPrincipal principal = SecurityTestIdentity.principal();
        return builder -> builder.defaultRequest(get("/")
                .with(user(principal))
                .with(csrf())
                .header(ActiveOrganizationContextFilter.ORGANIZATION_HEADER,
                        SecurityTestIdentity.ORGANIZATION_ID.toString()));
    }
}
