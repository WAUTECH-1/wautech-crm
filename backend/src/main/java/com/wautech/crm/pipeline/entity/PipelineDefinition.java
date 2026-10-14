package com.wautech.crm.pipeline.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "pipeline_definition", schema = "crm")
public class PipelineDefinition {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(nullable = false, updatable = false)
  private UUID organizationId;

  @Column(nullable = false, length = 200)
  private String name;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private List<Stage> stages;

  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant updatedAt;

  @Column(nullable = false)
  private boolean archived;

  public record Stage(String key, String name, String outcome) {}

  protected PipelineDefinition() {}

  public PipelineDefinition(UUID org, String name, List<Stage> stages) {
    organizationId = org;
    this.name = name;
    this.stages = List.copyOf(stages);
    createdAt = Instant.now();
    updatedAt = createdAt;
  }

  public void update(String name, List<Stage> stages) {
    this.name = name;
    this.stages = List.copyOf(stages);
    updatedAt = Instant.now();
  }

  public void archive() {
    archived = true;
    updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public String getName() {
    return name;
  }

  public List<Stage> getStages() {
    return stages;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public boolean isArchived() {
    return archived;
  }
}
