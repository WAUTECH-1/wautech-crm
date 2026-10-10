package com.wautech.crm.platform.tenant;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.UUID;

/** Reads only the server-populated request attribute planned for the authentication feature. */
@Component
@RequestScope
public class AuthenticatedOrganizationContext {
    public static final String REQUEST_ATTRIBUTE = AuthenticatedOrganizationContext.class.getName() + ".organizationId";

    private final HttpServletRequest request;

    public AuthenticatedOrganizationContext(HttpServletRequest request) {
        this.request = request;
    }

    public UUID requireOrganizationId() {
        Object value = request.getAttribute(REQUEST_ATTRIBUTE);
        if (value instanceof UUID organizationId) {
            return organizationId;
        }
        throw new OrganizationContextUnavailableException();
    }
}
