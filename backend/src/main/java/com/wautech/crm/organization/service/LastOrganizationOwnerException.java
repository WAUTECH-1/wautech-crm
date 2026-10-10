package com.wautech.crm.organization.service;

import java.util.UUID;

public class LastOrganizationOwnerException extends RuntimeException {
    public LastOrganizationOwnerException(UUID organizationId) {
        super("The final active organization owner cannot be removed or demoted: " + organizationId);
    }
}
