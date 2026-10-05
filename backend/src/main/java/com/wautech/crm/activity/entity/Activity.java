package com.wautech.crm.activity.entity;

import com.wautech.crm.company.entity.Company;
import com.wautech.crm.contact.entity.Contact;
import com.wautech.crm.lead.entity.Lead;
import com.wautech.crm.opportunity.entity.Opportunity;
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

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "activity", schema = "crm")
public class Activity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id")
    private Contact contact;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lead_id")
    private Lead lead;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "opportunity_id")
    private Opportunity opportunity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ActivityType type;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private Instant occurredAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column(nullable = false)
    private boolean archived;

    protected Activity() {
    }

    public Activity(Company company, Contact contact, Lead lead, Opportunity opportunity, ActivityType type,
                    String subject, String description, Instant occurredAt) {
        this.company = company;
        this.contact = contact;
        this.lead = lead;
        this.opportunity = opportunity;
        this.type = type;
        this.subject = subject;
        this.description = description;
        this.occurredAt = occurredAt;
    }

    public void update(Company company, Contact contact, Lead lead, Opportunity opportunity, ActivityType type,
                       String subject, String description, Instant occurredAt) {
        this.company = company;
        this.contact = contact;
        this.lead = lead;
        this.opportunity = opportunity;
        this.type = type;
        this.subject = subject;
        this.description = description;
        this.occurredAt = occurredAt;
        this.updatedAt = Instant.now();
    }

    public void archive() {
        this.archived = true;
        this.updatedAt = Instant.now();
    }

    @PrePersist
    void setCreationTimestamps() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    public UUID getId() { return id; }
    public Company getCompany() { return company; }
    public Contact getContact() { return contact; }
    public Lead getLead() { return lead; }
    public Opportunity getOpportunity() { return opportunity; }
    public ActivityType getType() { return type; }
    public String getSubject() { return subject; }
    public String getDescription() { return description; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public boolean isArchived() { return archived; }
}
