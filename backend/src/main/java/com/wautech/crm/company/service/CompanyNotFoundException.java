package com.wautech.crm.company.service;

import java.util.UUID;

public class CompanyNotFoundException extends RuntimeException {
    public CompanyNotFoundException(UUID id) {
        super("Company with ID '" + id + "' was not found");
    }
}
