package com.wautech.crm.organization.entity;

public class IllegalMembershipTransitionException extends RuntimeException {
    public IllegalMembershipTransitionException(MembershipStatus from, MembershipStatus to) {
        super("Membership cannot transition from " + from + " to " + to);
    }
}
