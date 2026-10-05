package com.wautech.crm.lead.entity;

public class IllegalLeadStatusTransitionException extends RuntimeException {
    public IllegalLeadStatusTransitionException(LeadStatus current, LeadStatus requested) {
        super("Lead status cannot transition from " + current + " to " + requested);
    }
}
