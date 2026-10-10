package com.wautech.crm.organization.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrganizationRoleTest {
    @Test
    void appliesTheApprovedCrmPermissionMatrix() {
        for (OrganizationRole role : OrganizationRole.values()) assertTrue(role.canViewCrm());
        assertTrue(OrganizationRole.OWNER.canWriteCrm());
        assertTrue(OrganizationRole.ADMIN.canWriteCrm());
        assertTrue(OrganizationRole.SALES_USER.canWriteCrm());
        assertFalse(OrganizationRole.VIEWER.canWriteCrm());
        assertTrue(OrganizationRole.OWNER.canManageOrganization());
        assertTrue(OrganizationRole.ADMIN.canManageOrganization());
        assertFalse(OrganizationRole.SALES_USER.canManageOrganization());
        assertFalse(OrganizationRole.VIEWER.canManageOrganization());
    }

    @Test
    void restrictsRoleAssignmentAndKeepsOwnerTransferSeparate() {
        assertTrue(OrganizationRole.OWNER.canAssign(OrganizationRole.ADMIN));
        assertTrue(OrganizationRole.OWNER.canAssign(OrganizationRole.SALES_USER));
        assertTrue(OrganizationRole.OWNER.canAssign(OrganizationRole.VIEWER));
        assertFalse(OrganizationRole.OWNER.canAssign(OrganizationRole.OWNER));
        assertTrue(OrganizationRole.ADMIN.canAssign(OrganizationRole.SALES_USER));
        assertTrue(OrganizationRole.ADMIN.canAssign(OrganizationRole.VIEWER));
        assertFalse(OrganizationRole.ADMIN.canAssign(OrganizationRole.ADMIN));
        assertFalse(OrganizationRole.ADMIN.canAssign(OrganizationRole.OWNER));
        assertFalse(OrganizationRole.SALES_USER.canAssign(OrganizationRole.VIEWER));
    }
}
