package com.wautech.crm.note.entity;

import com.wautech.crm.company.entity.Company;
import com.wautech.crm.contact.entity.Contact;
import com.wautech.crm.lead.entity.Lead;
import com.wautech.crm.opportunity.entity.Opportunity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "note", schema = "crm")
public class Note {
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

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column(nullable = false)
    private boolean archived;

    protected Note() {
    }

    public Note(Company company, Contact contact, Lead lead, Opportunity opportunity, String title, String body) {
        this.company = company;
        this.contact = contact;
        this.lead = lead;
        this.opportunity = opportunity;
        this.title = title;
        this.body = body;
    }

    public void update(Company company, Contact contact, Lead lead, Opportunity opportunity, String title, String body) {
        this.company = company;
        this.contact = contact;
        this.lead = lead;
        this.opportunity = opportunity;
        this.title = title;
        this.body = body;
        this.updatedAt = Instant.now();
    }

    public void archive() {
        archived = true;
        updatedAt = Instant.now();
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
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public boolean isArchived() { return archived; }
}
