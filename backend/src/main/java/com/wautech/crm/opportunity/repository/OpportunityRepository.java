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
            "and (:stage is null or o.stage = :stage) order by o.createdAt desc")
    List<Opportunity> findActive(@Param("companyId") UUID companyId,
                                 @Param("contactId") UUID contactId,
                                 @Param("stage") OpportunityStage stage);

    Optional<Opportunity> findByIdAndArchivedFalse(UUID id);
}
