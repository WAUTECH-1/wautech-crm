package com.wautech.crm.audit.repository;

import com.wautech.crm.audit.entity.AuditEvent;
import com.wautech.crm.audit.entity.AuditOutcome;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {
    @Query("""
            select e from AuditEvent e
            where e.organizationId = :organizationId
              and (:fromTime is null or e.occurredAt >= :fromTime)
              and (:toTime is null or e.occurredAt <= :toTime)
              and (:eventType is null or e.eventType = :eventType)
              and (:actorId is null or e.actorUserId = :actorId)
              and (:targetType is null or e.targetType = :targetType)
              and (:targetId is null or e.targetId = :targetId)
              and (:outcome is null or e.outcome = :outcome)
            """)
    Page<AuditEvent> findOrganizationEvents(@Param("organizationId") UUID organizationId,
            @Param("fromTime") Instant fromTime, @Param("toTime") Instant toTime,
            @Param("eventType") String eventType, @Param("actorId") UUID actorId,
            @Param("targetType") String targetType, @Param("targetId") UUID targetId,
            @Param("outcome") AuditOutcome outcome, Pageable pageable);
}
