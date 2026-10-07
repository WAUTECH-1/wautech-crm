package com.wautech.crm.savedview.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "saved_view", schema = "crm")
public class SavedView {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SavedViewResource resource;

    @Column(nullable = false)
    private int configurationVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private JsonNode configuration;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column(nullable = false)
    private boolean archived;

    protected SavedView() {
    }

    public SavedView(String name, SavedViewResource resource, int configurationVersion, JsonNode configuration) {
        this.name = name;
        this.resource = resource;
        this.configurationVersion = configurationVersion;
        this.configuration = configuration.deepCopy();
    }

    public void update(String name, SavedViewResource resource, int configurationVersion, JsonNode configuration) {
        this.name = name;
        this.resource = resource;
        this.configurationVersion = configurationVersion;
        this.configuration = configuration.deepCopy();
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
    public String getName() { return name; }
    public SavedViewResource getResource() { return resource; }
    public int getConfigurationVersion() { return configurationVersion; }
    public JsonNode getConfiguration() { return configuration; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public boolean isArchived() { return archived; }
}
