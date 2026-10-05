package com.wautech.crm.lead.entity;

import com.wautech.crm.company.entity.Company;
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
@Table(name = "lead", schema = "crm")
public class Lead {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @Column(nullable = false, length = 100)
    private String firstName;

    @Column(nullable = false, length = 100)
    private String lastName;

    @Column(length = 254)
    private String email;

    @Column(length = 40)
    private String phone;

    @Column(length = 120)
    private String jobTitle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LeadStatus status = LeadStatus.NEW;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column(nullable = false)
    private boolean archived;

    protected Lead() {
    }

    public Lead(Company company, String firstName, String lastName, String email, String phone, String jobTitle) {
        this.company = company;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        this.jobTitle = jobTitle;
    }

    public void update(Company company, String firstName, String lastName, String email, String phone, String jobTitle) {
        this.company = company;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        this.jobTitle = jobTitle;
        this.updatedAt = Instant.now();
    }

    public void changeStatus(LeadStatus newStatus) {
        boolean allowed = switch (status) {
            case NEW -> newStatus == LeadStatus.CONTACTED || newStatus == LeadStatus.QUALIFIED
                    || newStatus == LeadStatus.DISQUALIFIED;
            case CONTACTED -> newStatus == LeadStatus.QUALIFIED || newStatus == LeadStatus.DISQUALIFIED;
            case QUALIFIED, DISQUALIFIED -> false;
        };
        if (!allowed) {
            throw new IllegalLeadStatusTransitionException(status, newStatus);
        }
        status = newStatus;
        updatedAt = Instant.now();
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
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getJobTitle() { return jobTitle; }
    public LeadStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public boolean isArchived() { return archived; }
}
