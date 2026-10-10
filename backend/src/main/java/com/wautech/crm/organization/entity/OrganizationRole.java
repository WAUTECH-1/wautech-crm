package com.wautech.crm.organization.entity;

/** Roles are scoped to one organization through OrganizationMembership. */
public enum OrganizationRole {
    OWNER,
    ADMIN,
    SALES_USER,
    VIEWER;

    public boolean canViewCrm() { return true; }
    public boolean canWriteCrm() { return this != VIEWER; }
    public boolean canManageOrganization() { return this == OWNER || this == ADMIN; }

    public boolean canAssign(OrganizationRole requestedRole) {
        if (this == OWNER) return requestedRole != OWNER;
        return this == ADMIN && (requestedRole == SALES_USER || requestedRole == VIEWER);
    }
}
