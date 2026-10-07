package com.wautech.crm.note.dto;

import com.wautech.crm.note.entity.Note;

import java.time.Instant;
import java.util.UUID;

public record NoteResponse(UUID id, UUID companyId, UUID contactId, UUID leadId, UUID opportunityId,
                           String title, String body, Instant createdAt, Instant updatedAt, boolean archived) {
    public static NoteResponse from(Note note) {
        return new NoteResponse(note.getId(), idOf(note.getCompany()), idOf(note.getContact()),
                idOf(note.getLead()), idOf(note.getOpportunity()), note.getTitle(), note.getBody(),
                note.getCreatedAt(), note.getUpdatedAt(), note.isArchived());
    }

    private static UUID idOf(com.wautech.crm.company.entity.Company company) {
        return company == null ? null : company.getId();
    }

    private static UUID idOf(com.wautech.crm.contact.entity.Contact contact) {
        return contact == null ? null : contact.getId();
    }

    private static UUID idOf(com.wautech.crm.lead.entity.Lead lead) {
        return lead == null ? null : lead.getId();
    }

    private static UUID idOf(com.wautech.crm.opportunity.entity.Opportunity opportunity) {
        return opportunity == null ? null : opportunity.getId();
    }
}
