package com.wautech.crm.identity.service;

public class InvalidPasswordException extends RuntimeException {
    public InvalidPasswordException() { super("Password does not meet the credential policy"); }
}
