package com.wautech.crm.contact.service;

import java.util.UUID;

public class ContactNotFoundException extends RuntimeException {
    public ContactNotFoundException(UUID id) {
        super("Contact with ID '" + id + "' was not found");
    }
}
