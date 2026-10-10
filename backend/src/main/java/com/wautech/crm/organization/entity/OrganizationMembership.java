package com.wautech.crm.organization.entity;

import com.wautech.crm.identity.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "organization_membership", schema = "crm",
        uniqueConstraints = @UniqueConstraint(name = "uq_membership_organization_user", columnNames = {"organization_id", "user_id"}))
public class OrganizationMembership {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MembershipStatus status = MembershipStatus.INVITED;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OrganizationRole role = OrganizationRole.VIEWER;

    private Instant invitedAt;
    private Instant joinedAt;
    private Instant deactivatedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected OrganizationMembership() { }

    public OrganizationMembership(Organization organization, User user) {
        this.organization = organization;
        this.user = user;
    }

    public void transitionTo(MembershipStatus nextStatus) {
        Instant now = Instant.now();
        switch (status) {
            case INVITED -> {
                if (nextStatus == MembershipStatus.ACTIVE) {
                    joinedAt = now;
                    deactivatedAt = null;
                } else if (nextStatus == MembershipStatus.REVOKED) {
                    deactivatedAt = now;
                } else {
                    throw new IllegalMembershipTransitionException(status, nextStatus);
                }
            }
            case ACTIVE -> {
                if (nextStatus == MembershipStatus.SUSPENDED || nextStatus == MembershipStatus.REVOKED) {
                    deactivatedAt = now;
                } else {
                    throw new IllegalMembershipTransitionException(status, nextStatus);
                }
            }
            case SUSPENDED -> {
                if (nextStatus == MembershipStatus.ACTIVE) {
                    deactivatedAt = null;
                } else if (nextStatus == MembershipStatus.REVOKED) {
                    deactivatedAt = now;
                } else {
                    throw new IllegalMembershipTransitionException(status, nextStatus);
                }
            }
            case REVOKED -> throw new IllegalMembershipTransitionException(status, nextStatus);
        }
        status = nextStatus;
        updatedAt = now;
    }

    public void changeRole(OrganizationRole nextRole) {
        this.role = nextRole;
        this.updatedAt = Instant.now();
    }

    @PrePersist
    void setCreationTimestamps() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (status == MembershipStatus.INVITED && invitedAt == null) invitedAt = now;
    }

    public UUID getId() { return id; }
    public Organization getOrganization() { return organization; }
    public User getUser() { return user; }
    public MembershipStatus getStatus() { return status; }
    public OrganizationRole getRole() { return role; }
    public Instant getInvitedAt() { return invitedAt; }
    public Instant getJoinedAt() { return joinedAt; }
    public Instant getDeactivatedAt() { return deactivatedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
