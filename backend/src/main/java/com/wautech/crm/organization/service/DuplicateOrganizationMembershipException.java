package com.wautech.crm.organization.service;

import java.util.UUID;

public class DuplicateOrganizationMembershipException extends RuntimeException {
    public DuplicateOrganizationMembershipException(UUID organizationId, UUID userId) {
        super("User " + userId + " already has a membership in organization " + organizationId);
    }
}
