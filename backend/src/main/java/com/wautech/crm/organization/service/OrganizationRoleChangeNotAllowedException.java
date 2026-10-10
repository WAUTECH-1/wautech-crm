package com.wautech.crm.organization.service;

public class OrganizationRoleChangeNotAllowedException extends RuntimeException {
    public OrganizationRoleChangeNotAllowedException() {
        super("The requested organization role change is not permitted");
    }
}
