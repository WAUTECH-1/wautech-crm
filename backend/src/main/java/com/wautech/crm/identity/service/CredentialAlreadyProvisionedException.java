package com.wautech.crm.identity.service;

import java.util.UUID;

public class CredentialAlreadyProvisionedException extends RuntimeException {
    public CredentialAlreadyProvisionedException(UUID userId) { super("Credential is already provisioned for user: " + userId); }
}
