package com.wautech.crm;

import com.wautech.crm.organization.entity.Organization;

import java.util.UUID;

public final class TestOrganization {
    public static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000011");
    public static final Organization ENTITY = new Organization("Test Organization");

    private TestOrganization() {
    }
}
