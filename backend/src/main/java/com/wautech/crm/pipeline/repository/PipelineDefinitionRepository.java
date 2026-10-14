package com.wautech.crm.pipeline.repository;

import com.wautech.crm.pipeline.entity.PipelineDefinition;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PipelineDefinitionRepository extends JpaRepository<PipelineDefinition, UUID> {
  List<PipelineDefinition> findByOrganizationIdAndArchivedFalseOrderByNameAsc(UUID id);

  Optional<PipelineDefinition> findByIdAndOrganizationIdAndArchivedFalse(UUID id, UUID org);

  boolean existsByOrganizationIdAndNameIgnoreCaseAndArchivedFalse(UUID org, String name);
}
