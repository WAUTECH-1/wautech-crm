package com.wautech.crm.organization.service;

import java.util.UUID;

public class OrganizationMembershipNotFoundException extends RuntimeException {
    public OrganizationMembershipNotFoundException(UUID id) { super("Organization membership not found: " + id); }
}
