package com.wautech.crm.organization.service;

import java.util.UUID;

public class OrganizationNotFoundException extends RuntimeException {
    public OrganizationNotFoundException(UUID id) {
        super("Organization not found: " + id);
    }
}
