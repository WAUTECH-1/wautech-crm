package com.wautech.crm.savedview.repository;

import com.wautech.crm.savedview.entity.SavedView;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SavedViewRepository extends JpaRepository<SavedView, UUID> {
    List<SavedView> findAllByArchivedFalseOrderByNameAscIdAsc();
    Optional<SavedView> findByIdAndArchivedFalse(UUID id);
}
