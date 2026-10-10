package com.wautech.crm.platform.test;

import com.wautech.crm.identity.entity.User;
import com.wautech.crm.identity.security.CrmUserPrincipal;

import java.util.UUID;

public final class SecurityTestIdentity {
    public static final UUID USER_ID = UUID.fromString("e0c19141-2922-4e88-8973-91f36b31c76d");
    public static final UUID ORGANIZATION_ID = UUID.fromString("00eb8dee-cb0f-490a-b7fc-841ea3dceac3");
    public static final String EMAIL = "test.user@example.test";

    private SecurityTestIdentity() { }

    public static User user() {
        return new User(EMAIL, "Test", "User");
    }

    public static CrmUserPrincipal principal() {
        return new CrmUserPrincipal(USER_ID, EMAIL, "Test", "User", "$2a$10$unprovisioned.test.hash.placeholder", true);
    }
}
