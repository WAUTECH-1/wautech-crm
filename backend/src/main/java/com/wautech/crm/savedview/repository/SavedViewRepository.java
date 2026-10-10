package com.wautech.crm.savedview.repository;

import com.wautech.crm.savedview.entity.SavedView;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SavedViewRepository extends JpaRepository<SavedView, UUID> {
    List<SavedView> findAllByOrganization_IdAndArchivedFalseOrderByNameAscIdAsc(UUID organizationId);
    Optional<SavedView> findByIdAndOrganization_IdAndArchivedFalse(UUID id, UUID organizationId);
}
