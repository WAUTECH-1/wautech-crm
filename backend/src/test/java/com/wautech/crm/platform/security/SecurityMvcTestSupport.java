package com.wautech.crm.platform.security;

import com.wautech.crm.identity.service.UserService;
import com.wautech.crm.organization.service.OrganizationMembershipService;
import com.wautech.crm.platform.test.SecurityTestIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.Mockito.when;

public abstract class SecurityMvcTestSupport {
    @MockitoBean protected UserService userService;
    @MockitoBean protected OrganizationMembershipService membershipService;
    @MockitoBean protected UserDetailsService userDetailsService;

    @BeforeEach
    protected void allowTestUserAndOrganization() {
        when(userService.requireEnabledUser(SecurityTestIdentity.USER_ID)).thenReturn(SecurityTestIdentity.user());
        when(membershipService.isActiveMember(SecurityTestIdentity.ORGANIZATION_ID, SecurityTestIdentity.USER_ID))
                .thenReturn(true);
    }
}
