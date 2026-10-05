package com.wautech.crm.lead.repository;

import com.wautech.crm.lead.entity.Lead;
import com.wautech.crm.lead.entity.LeadStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeadRepository extends JpaRepository<Lead, UUID> {
    List<Lead> findAllByArchivedFalseOrderByCreatedAtDesc();
    List<Lead> findAllByArchivedFalseAndStatusOrderByCreatedAtDesc(LeadStatus status);
    List<Lead> findAllByCompany_IdAndArchivedFalseOrderByCreatedAtDesc(UUID companyId);
    List<Lead> findAllByCompany_IdAndArchivedFalseAndStatusOrderByCreatedAtDesc(UUID companyId, LeadStatus status);
    Optional<Lead> findByIdAndArchivedFalse(UUID id);
}
