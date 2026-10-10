package com.wautech.crm.company.entity;

import com.wautech.crm.organization.entity.Organization;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "company", schema = "crm")
public class Company {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 2048)
    private String website;

    @Column(length = 120)
    private String industry;

    @Column(length = 40)
    private String phone;

    @Column(length = 254)
    private String email;

    @Column(nullable = false, length = 40)
    private String status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column(nullable = false)
    private boolean archived;

    protected Company() {
    }

    public Company(Organization organization, String name, String website, String industry, String phone, String email, String status) {
        this.organization = organization;
        this.name = name;
        this.website = website;
        this.industry = industry;
        this.phone = phone;
        this.email = email;
        this.status = status;
    }

    public void update(String name, String website, String industry, String phone, String email, String status) {
        this.name = name;
        this.website = website;
        this.industry = industry;
        this.phone = phone;
        this.email = email;
        this.status = status;
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
    public Organization getOrganization() { return organization; }
    public String getName() { return name; }
    public String getWebsite() { return website; }
    public String getIndustry() { return industry; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public boolean isArchived() { return archived; }
}
