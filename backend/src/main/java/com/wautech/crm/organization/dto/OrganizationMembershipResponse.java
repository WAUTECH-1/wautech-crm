package com.wautech.crm.organization.dto;

import com.wautech.crm.organization.entity.MembershipStatus;
import com.wautech.crm.organization.entity.OrganizationMembership;

import java.time.Instant;
import java.util.UUID;

public record OrganizationMembershipResponse(UUID id, UUID organizationId, UUID userId,
        MembershipStatus status, Instant invitedAt, Instant joinedAt, Instant deactivatedAt,
        Instant createdAt, Instant updatedAt) {
    public static OrganizationMembershipResponse from(OrganizationMembership membership) {
        return new OrganizationMembershipResponse(membership.getId(), membership.getOrganization().getId(),
                membership.getUser().getId(), membership.getStatus(), membership.getInvitedAt(),
                membership.getJoinedAt(), membership.getDeactivatedAt(), membership.getCreatedAt(), membership.getUpdatedAt());
    }
}
