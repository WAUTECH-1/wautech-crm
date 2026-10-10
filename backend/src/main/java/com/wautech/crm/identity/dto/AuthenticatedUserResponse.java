package com.wautech.crm.identity.dto;

import com.wautech.crm.identity.security.CrmUserPrincipal;

import java.util.UUID;

public record AuthenticatedUserResponse(UUID id, String email, String firstName, String lastName) {
    public static AuthenticatedUserResponse from(CrmUserPrincipal user) {
        return new AuthenticatedUserResponse(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName());
    }
}
