package com.wautech.crm.platform.tenant;

public class OrganizationContextUnavailableException extends RuntimeException {
    public OrganizationContextUnavailableException() {
        super("Authenticated organization context is not available");
    }
}
