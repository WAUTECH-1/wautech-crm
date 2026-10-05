package com.wautech.crm.lead.service;

import java.util.UUID;

public class LeadNotFoundException extends RuntimeException {
    public LeadNotFoundException(UUID id) {
        super("Lead with ID '" + id + "' was not found");
    }
}
