package com.wautech.crm.opportunity.entity;

import com.wautech.crm.company.entity.Company;
import com.wautech.crm.contact.entity.Contact;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "opportunity", schema = "crm")
public class Opportunity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OpportunityStage stage;

    @Column
    private LocalDate expectedCloseDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contact_id")
    private Contact contact;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column(nullable = false)
    private boolean archived;

    protected Opportunity() {
    }

    public Opportunity(String name, String description, BigDecimal amount, String currency,
                       OpportunityStage stage, LocalDate expectedCloseDate, Company company, Contact contact) {
        this.name = name;
        this.description = description;
        this.amount = amount;
        this.currency = currency;
        this.stage = stage == null ? OpportunityStage.QUALIFICATION : stage;
        this.expectedCloseDate = expectedCloseDate;
        this.company = company;
        this.contact = contact;
    }

    public void update(String name, String description, BigDecimal amount, String currency,
                       LocalDate expectedCloseDate, Company company, Contact contact) {
        this.name = name;
        this.description = description;
        this.amount = amount;
        this.currency = currency;
        this.expectedCloseDate = expectedCloseDate;
        this.company = company;
        this.contact = contact;
        this.updatedAt = Instant.now();
    }

    public void changeStage(OpportunityStage requested) {
        boolean allowed = switch (stage) {
            case QUALIFICATION -> requested == OpportunityStage.NEEDS_ANALYSIS || requested == OpportunityStage.CLOSED_LOST;
            case NEEDS_ANALYSIS -> requested == OpportunityStage.PROPOSAL || requested == OpportunityStage.CLOSED_LOST;
            case PROPOSAL -> requested == OpportunityStage.NEGOTIATION || requested == OpportunityStage.CLOSED_LOST;
            case NEGOTIATION -> requested == OpportunityStage.CLOSED_WON || requested == OpportunityStage.CLOSED_LOST;
            case CLOSED_WON, CLOSED_LOST -> false;
        };
        if (!allowed) {
            throw new IllegalOpportunityStageTransitionException(stage, requested);
        }
        this.stage = requested;
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
    public String getName() { return name; }
    public String getDescription() { return description; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public OpportunityStage getStage() { return stage; }
    public LocalDate getExpectedCloseDate() { return expectedCloseDate; }
    public Company getCompany() { return company; }
    public Contact getContact() { return contact; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public boolean isArchived() { return archived; }
}
