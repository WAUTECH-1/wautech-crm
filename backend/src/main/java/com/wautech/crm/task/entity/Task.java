package com.wautech.crm.task.entity;

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
@Table(name = "task", schema = "crm")
public class Task {
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

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskPriority priority;

    private Instant dueAt;

    private Instant completedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column(nullable = false)
    private boolean archived;

    protected Task() {
    }

    public Task(Company company, Contact contact, Lead lead, Opportunity opportunity, String title,
                String description, TaskPriority priority, Instant dueAt) {
        this.company = company;
        this.contact = contact;
        this.lead = lead;
        this.opportunity = opportunity;
        this.title = title;
        this.description = description;
        this.status = TaskStatus.OPEN;
        this.priority = priority == null ? TaskPriority.NORMAL : priority;
        this.dueAt = dueAt;
    }

    public void update(Company company, Contact contact, Lead lead, Opportunity opportunity, String title,
                       String description, TaskPriority priority, Instant dueAt) {
        this.company = company;
        this.contact = contact;
        this.lead = lead;
        this.opportunity = opportunity;
        this.title = title;
        this.description = description;
        this.priority = priority == null ? TaskPriority.NORMAL : priority;
        this.dueAt = dueAt;
        this.updatedAt = Instant.now();
    }

    public void changeStatus(TaskStatus requested) {
        boolean allowed = switch (status) {
            case OPEN -> requested == TaskStatus.IN_PROGRESS || requested == TaskStatus.COMPLETED
                    || requested == TaskStatus.CANCELLED;
            case IN_PROGRESS -> requested == TaskStatus.OPEN || requested == TaskStatus.COMPLETED
                    || requested == TaskStatus.CANCELLED;
            case COMPLETED, CANCELLED -> requested == TaskStatus.OPEN;
        };
        if (!allowed) {
            throw new IllegalTaskStatusTransitionException(status, requested);
        }

        this.status = requested;
        this.completedAt = requested == TaskStatus.COMPLETED ? Instant.now() : null;
        this.updatedAt = Instant.now();
    }

    public void archive() {
        this.archived = true;
        this.updatedAt = Instant.now();
    }

    public boolean isOverdueAt(Instant now) {
        return dueAt != null && dueAt.isBefore(now)
                && (status == TaskStatus.OPEN || status == TaskStatus.IN_PROGRESS);
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
    public String getDescription() { return description; }
    public TaskStatus getStatus() { return status; }
    public TaskPriority getPriority() { return priority; }
    public Instant getDueAt() { return dueAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public boolean isArchived() { return archived; }
}
