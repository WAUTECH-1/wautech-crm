package com.wautech.crm.opportunity.repository;

import com.wautech.crm.opportunity.entity.Opportunity;
import com.wautech.crm.opportunity.entity.OpportunityStage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OpportunityRepository extends JpaRepository<Opportunity, UUID> {
    @Query("select o from Opportunity o where o.archived = false " +
            "and (:companyId is null or o.company.id = :companyId) " +
            "and (:contactId is null or o.contact.id = :contactId) " +
            "and (:stage is null or o.stage = :stage) " +
            "and (:search is null or lower(o.name) like :search escape '!' or lower(o.description) like :search escape '!') " +
            "order by o.createdAt desc")
    List<Opportunity> findActive(@Param("companyId") UUID companyId,
                                 @Param("contactId") UUID contactId,
                                 @Param("stage") OpportunityStage stage,
                                 @Param("search") String search);

    default List<Opportunity> findActive(UUID companyId, UUID contactId, OpportunityStage stage) {
        return findActive(companyId, contactId, stage, null);
    }

    Optional<Opportunity> findByIdAndArchivedFalse(UUID id);
}
