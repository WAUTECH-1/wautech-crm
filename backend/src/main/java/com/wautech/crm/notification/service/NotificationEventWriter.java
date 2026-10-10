package com.wautech.crm.notification.service;

import com.wautech.crm.notification.entity.NotificationTargetType;
import com.wautech.crm.notification.entity.NotificationType;
import com.wautech.crm.organization.entity.MembershipStatus;
import com.wautech.crm.organization.entity.OrganizationMembership;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Emits only events with a concrete membership recipient in the current domain model. */
@Component
public class NotificationEventWriter {
    private final NotificationService notificationService;

    public NotificationEventWriter(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    public void membershipActivated(OrganizationMembership membership) {
        if (membership.getStatus() != MembershipStatus.ACTIVE) return;
        notificationService.createFromTrustedEvent(membership.getOrganization().getId(),
                membership.getUser().getId(), NotificationType.ORGANIZATION_MEMBERSHIP_ACTIVATED,
                "Your organization access is active", "You can now access this organization’s CRM workspace.",
                NotificationTargetType.ORGANIZATION_MEMBERSHIP, membership.getId(),
                deduplicationKey("activated", membership));
    }

    public void membershipRoleChanged(OrganizationMembership membership) {
        if (membership.getStatus() != MembershipStatus.ACTIVE) return;
        notificationService.createFromTrustedEvent(membership.getOrganization().getId(),
                membership.getUser().getId(), NotificationType.ORGANIZATION_MEMBERSHIP_ROLE_CHANGED,
                "Your organization role changed", "Your organization role was updated to " + membership.getRole() + ".",
                NotificationTargetType.ORGANIZATION_MEMBERSHIP, membership.getId(),
                deduplicationKey("role-" + membership.getRole().name(), membership));
    }

    private String deduplicationKey(String action, OrganizationMembership membership) {
        UUID membershipId = membership.getId();
        return "membership:" + membershipId + ":" + action + ":" + membership.getUpdatedAt();
    }
}
