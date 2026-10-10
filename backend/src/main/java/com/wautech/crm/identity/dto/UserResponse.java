package com.wautech.crm.identity.dto;

import com.wautech.crm.identity.entity.User;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(UUID id, String email, String firstName, String lastName,
                           boolean enabled, Instant createdAt, Instant updatedAt) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(),
                user.isEnabled(), user.getCreatedAt(), user.getUpdatedAt());
    }
}
