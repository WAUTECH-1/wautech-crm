package com.wautech.crm.activity.repository;

import com.wautech.crm.activity.entity.Activity;
import com.wautech.crm.activity.entity.ActivityType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ActivityRepository extends JpaRepository<Activity, UUID> {
    @Query("select a from Activity a where a.archived = false " +
            "and (:companyId is null or a.company.id = :companyId) " +
            "and (:contactId is null or a.contact.id = :contactId) " +
            "and (:leadId is null or a.lead.id = :leadId) " +
            "and (:opportunityId is null or a.opportunity.id = :opportunityId) " +
            "and (:type is null or a.type = :type) order by a.occurredAt desc")
    List<Activity> findActive(@Param("companyId") UUID companyId,
                              @Param("contactId") UUID contactId,
                              @Param("leadId") UUID leadId,
                              @Param("opportunityId") UUID opportunityId,
                              @Param("type") ActivityType type);

    Optional<Activity> findByIdAndArchivedFalse(UUID id);
}
