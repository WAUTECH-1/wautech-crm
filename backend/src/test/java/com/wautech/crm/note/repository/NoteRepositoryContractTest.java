package com.wautech.crm.note.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NoteRepositoryContractTest {
    @Test
    void activeListQueryExcludesArchivedNotesAndCombinesParentFiltersWithAnd() throws Exception {
        String query = activeListQuery();

        assertTrue(query.contains("n.archived = false"));
        assertTrue(query.contains("n.organization.id = :organizationId"));
        assertTrue(query.contains("and (:companyId is null or n.company.id = :companyId)"));
        assertTrue(query.contains("and (:contactId is null or n.contact.id = :contactId)"));
        assertTrue(query.contains("and (:leadId is null or n.lead.id = :leadId)"));
        assertTrue(query.contains("and (:opportunityId is null or n.opportunity.id = :opportunityId)"));
        assertTrue(!query.contains(" or (:contactId"));
        assertTrue(!query.contains(" or (:leadId"));
        assertTrue(!query.contains(" or (:opportunityId"));
    }

    @Test
    void activeListQueryOrdersByUpdatedAtThenCreatedAtDescending() throws Exception {
        String query = activeListQuery();

        assertTrue(query.endsWith("order by n.updatedAt desc, n.createdAt desc"));
    }

    @Test
    void getByIdQueryRequiresAnActiveNote() throws Exception {
        Method method = NoteRepository.class.getMethod("findByIdAndOrganization_IdAndArchivedFalse", java.util.UUID.class, java.util.UUID.class);

        assertTrue(method.getName().equals("findByIdAndOrganization_IdAndArchivedFalse"));
    }

    private String activeListQuery() throws Exception {
        Method method = NoteRepository.class.getMethod("findActive", java.util.UUID.class, java.util.UUID.class,
                java.util.UUID.class, java.util.UUID.class, java.util.UUID.class);
        return method.getAnnotation(Query.class).value().replaceAll("\\s+", " ").trim();
    }
}
