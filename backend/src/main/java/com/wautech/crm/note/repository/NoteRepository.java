package com.wautech.crm.note.repository;

import com.wautech.crm.note.entity.Note;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NoteRepository extends JpaRepository<Note, UUID> {
    @Query("select n from Note n where n.archived = false " +
            "and (:companyId is null or n.company.id = :companyId) " +
            "and (:contactId is null or n.contact.id = :contactId) " +
            "and (:leadId is null or n.lead.id = :leadId) " +
            "and (:opportunityId is null or n.opportunity.id = :opportunityId) " +
            "and (:search is null or lower(n.title) like :search escape '!' or lower(n.body) like :search escape '!') " +
            "order by n.updatedAt desc, n.createdAt desc")
    List<Note> findActive(@Param("companyId") UUID companyId,
                          @Param("contactId") UUID contactId,
                          @Param("leadId") UUID leadId,
                          @Param("opportunityId") UUID opportunityId,
                          @Param("search") String search);

    @Query("select n from Note n where n.archived = false " +
            "and (:companyId is null or n.company.id = :companyId) " +
            "and (:contactId is null or n.contact.id = :contactId) " +
            "and (:leadId is null or n.lead.id = :leadId) " +
            "and (:opportunityId is null or n.opportunity.id = :opportunityId) " +
            "order by n.updatedAt desc, n.createdAt desc")
    List<Note> findActive(UUID companyId, UUID contactId, UUID leadId, UUID opportunityId);

    Optional<Note> findByIdAndArchivedFalse(UUID id);
}
