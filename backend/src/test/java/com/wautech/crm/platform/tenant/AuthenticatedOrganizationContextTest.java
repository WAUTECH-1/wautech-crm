package com.wautech.crm.platform.tenant;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthenticatedOrganizationContextTest {
    @Test
    void requiresARequestAttributeThatOnlyServerAuthenticationCanPopulate() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        UUID organizationId = UUID.randomUUID();
        when(request.getAttribute(AuthenticatedOrganizationContext.REQUEST_ATTRIBUTE)).thenReturn(organizationId);

        assertEquals(organizationId, new AuthenticatedOrganizationContext(request).requireOrganizationId());
    }

    @Test
    void failsClosedWhenTheTrustedAttributeIsMissingOrMalformed() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        AuthenticatedOrganizationContext context = new AuthenticatedOrganizationContext(request);

        assertThrows(OrganizationContextUnavailableException.class, context::requireOrganizationId);
        when(request.getAttribute(AuthenticatedOrganizationContext.REQUEST_ATTRIBUTE)).thenReturn("tenant-from-client");
        assertThrows(OrganizationContextUnavailableException.class, context::requireOrganizationId);
    }
}
