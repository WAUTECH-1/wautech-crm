package com.wautech.crm.opportunity.entity;

public class IllegalOpportunityStageTransitionException extends RuntimeException {
    public IllegalOpportunityStageTransitionException(OpportunityStage current, OpportunityStage requested) {
        super("Opportunity stage cannot transition from " + current + " to " + requested);
    }
}
