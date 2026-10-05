package com.wautech.crm.opportunity.service;

import java.util.UUID;

public class OpportunityContactCompanyMismatchException extends RuntimeException {
    public OpportunityContactCompanyMismatchException(UUID contactId, UUID companyId) {
        super("Contact with ID '" + contactId + "' does not belong to Company with ID '" + companyId + "'");
    }
}
