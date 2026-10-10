package com.wautech.crm.identity.service;

import java.util.UUID;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(UUID id) { super("User not found: " + id); }
}
