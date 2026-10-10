package com.wautech.crm.identity.security;

/** A per-process random BCrypt hash used to verify unprovisioned users without enabling login. */
public record UnprovisionedPasswordHash(String value) { }
