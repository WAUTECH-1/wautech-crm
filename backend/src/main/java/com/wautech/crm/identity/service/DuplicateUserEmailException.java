package com.wautech.crm.identity.service;

public class DuplicateUserEmailException extends RuntimeException {
    public DuplicateUserEmailException(String email) { super("User email is already registered: " + email); }
}
