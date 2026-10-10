package com.wautech.crm.identity.service;

import java.util.UUID;

public class DisabledUserException extends RuntimeException {
    public DisabledUserException(UUID id) { super("User is disabled: " + id); }
}
