package com.wautech.crm.lead.repository;

import com.wautech.crm.lead.entity.Lead;
import com.wautech.crm.lead.entity.LeadStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeadRepository extends JpaRepository<Lead, UUID> {
    List<Lead> findAllByOrganization_IdAndArchivedFalseOrderByCreatedAtDesc(UUID organizationId);
    List<Lead> findAllByOrganization_IdAndArchivedFalseAndStatusOrderByCreatedAtDesc(UUID organizationId, LeadStatus status);
    List<Lead> findAllByOrganization_IdAndCompany_IdAndArchivedFalseOrderByCreatedAtDesc(UUID organizationId, UUID companyId);
    List<Lead> findAllByOrganization_IdAndCompany_IdAndArchivedFalseAndStatusOrderByCreatedAtDesc(UUID organizationId, UUID companyId, LeadStatus status);
    @Query("select l from Lead l where l.organization.id = :organizationId and l.archived = false and (:companyId is null or l.company.id = :companyId) " +
            "and (:status is null or l.status = :status) and (:search is null or lower(l.firstName) like :search escape '!' " +
            "or lower(l.lastName) like :search escape '!' or lower(l.email) like :search escape '!' " +
            "or lower(l.phone) like :search escape '!' or lower(l.jobTitle) like :search escape '!') " +
            "order by l.createdAt desc")
    List<Lead> findActive(@Param("organizationId") UUID organizationId, @Param("companyId") UUID companyId, @Param("status") LeadStatus status,
                          @Param("search") String search);
    Optional<Lead> findByIdAndOrganization_IdAndArchivedFalse(UUID id, UUID organizationId);
}
