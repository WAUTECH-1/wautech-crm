package com.wautech.crm.note.service;

import com.wautech.crm.company.entity.Company;
import com.wautech.crm.company.repository.CompanyRepository;
import com.wautech.crm.company.service.CompanyNotFoundException;
import com.wautech.crm.contact.entity.Contact;
import com.wautech.crm.contact.repository.ContactRepository;
import com.wautech.crm.contact.service.ContactNotFoundException;
import com.wautech.crm.lead.entity.Lead;
import com.wautech.crm.lead.repository.LeadRepository;
import com.wautech.crm.lead.service.LeadNotFoundException;
import com.wautech.crm.note.dto.NoteRequest;
import com.wautech.crm.note.dto.NoteResponse;
import com.wautech.crm.note.entity.Note;
import com.wautech.crm.note.repository.NoteRepository;
import com.wautech.crm.opportunity.entity.Opportunity;
import com.wautech.crm.opportunity.repository.OpportunityRepository;
import com.wautech.crm.opportunity.service.OpportunityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NoteServiceTest {
    @Mock private NoteRepository noteRepository;
    @Mock private CompanyRepository companyRepository;
    @Mock private ContactRepository contactRepository;
    @Mock private LeadRepository leadRepository;
    @Mock private OpportunityRepository opportunityRepository;
    @InjectMocks private NoteService noteService;

    @Test
    void createsNoteForEachSupportedParentAndTrimsTitle() {
        UUID companyId = UUID.randomUUID();
        UUID contactId = UUID.randomUUID();
        UUID leadId = UUID.randomUUID();
        UUID opportunityId = UUID.randomUUID();
        Company company = company(companyId);
        Contact contact = contact(contactId);
        Lead lead = lead(leadId);
        Opportunity opportunity = opportunity(opportunityId);
        when(companyRepository.findByIdAndArchivedFalse(companyId)).thenReturn(Optional.of(company));
        when(contactRepository.findByIdAndArchivedFalse(contactId)).thenReturn(Optional.of(contact));
        when(leadRepository.findByIdAndArchivedFalse(leadId)).thenReturn(Optional.of(lead));
        when(opportunityRepository.findByIdAndArchivedFalse(opportunityId)).thenReturn(Optional.of(opportunity));
        when(noteRepository.save(any(Note.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NoteResponse companyNote = noteService.create(request(companyId, null, null, null, "  Company note  ", "Body"));
        NoteResponse contactNote = noteService.create(request(null, contactId, null, null, "Contact", "Body"));
        NoteResponse leadNote = noteService.create(request(null, null, leadId, null, "Lead", "Body"));
        NoteResponse opportunityNote = noteService.create(request(null, null, null, opportunityId, "Opportunity", "Body"));

        assertEquals("Company note", companyNote.title());
        assertEquals(companyId, companyNote.companyId());
        assertEquals(contactId, contactNote.contactId());
        assertEquals(leadId, leadNote.leadId());
        assertEquals(opportunityId, opportunityNote.opportunityId());
        verify(noteRepository, times(4)).save(any(Note.class));
    }

    @Test
    void rejectsArchivedParentsThroughActiveOnlyLookups() {
        UUID companyId = UUID.randomUUID();
        UUID contactId = UUID.randomUUID();
        UUID leadId = UUID.randomUUID();
        UUID opportunityId = UUID.randomUUID();
        when(companyRepository.findByIdAndArchivedFalse(companyId)).thenReturn(Optional.empty());
        when(contactRepository.findByIdAndArchivedFalse(contactId)).thenReturn(Optional.empty());
        when(leadRepository.findByIdAndArchivedFalse(leadId)).thenReturn(Optional.empty());
        when(opportunityRepository.findByIdAndArchivedFalse(opportunityId)).thenReturn(Optional.empty());

        assertThrows(CompanyNotFoundException.class, () -> noteService.create(request(companyId, null, null, null, "Title", "Body")));
        assertThrows(ContactNotFoundException.class, () -> noteService.create(request(null, contactId, null, null, "Title", "Body")));
        assertThrows(LeadNotFoundException.class, () -> noteService.create(request(null, null, leadId, null, "Title", "Body")));
        assertThrows(OpportunityNotFoundException.class, () -> noteService.create(request(null, null, null, opportunityId, "Title", "Body")));
        verify(noteRepository, never()).save(any());
        verify(companyRepository).findByIdAndArchivedFalse(companyId);
        verify(contactRepository).findByIdAndArchivedFalse(contactId);
        verify(leadRepository).findByIdAndArchivedFalse(leadId);
        verify(opportunityRepository).findByIdAndArchivedFalse(opportunityId);
    }

    @Test
    void getsAndUpdatesActiveNoteIncludingParent() {
        UUID id = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        UUID contactId = UUID.randomUUID();
        Note note = new Note(company(companyId), null, null, null, "Before", "Old body");
        when(noteRepository.findByIdAndArchivedFalse(id)).thenReturn(Optional.of(note));
        when(contactRepository.findByIdAndArchivedFalse(contactId)).thenReturn(Optional.of(contact(contactId)));
        when(noteRepository.save(note)).thenReturn(note);

        assertEquals("Before", noteService.getById(id).title());
        NoteResponse updated = noteService.update(id, request(null, contactId, null, null, "After", "New body"));

        assertEquals("After", updated.title());
        assertEquals("New body", updated.body());
        assertNull(updated.companyId());
        assertEquals(contactId, updated.contactId());
    }

    @Test
    void rejectsUpdatingNoteToMissingOrArchivedParent() {
        UUID id = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        Note note = new Note(null, null, null, opportunity(UUID.randomUUID()), "Before", "Body");
        when(noteRepository.findByIdAndArchivedFalse(id)).thenReturn(Optional.of(note));
        when(companyRepository.findByIdAndArchivedFalse(companyId)).thenReturn(Optional.empty());

        assertThrows(CompanyNotFoundException.class,
                () -> noteService.update(id, request(companyId, null, null, null, "After", "Body")));
        verify(noteRepository, never()).save(any());
    }

    @Test
    void returnsOnlyActiveNotesUsingAllFilters() {
        UUID companyId = UUID.randomUUID();
        UUID contactId = UUID.randomUUID();
        UUID leadId = UUID.randomUUID();
        UUID opportunityId = UUID.randomUUID();
        Note newer = new Note(company(companyId), null, null, null, "Newer", "Body");
        Note older = new Note(null, contact(contactId), null, null, "Older", "Body");
        when(noteRepository.findActive(companyId, contactId, leadId, opportunityId)).thenReturn(List.of(newer, older));

        List<NoteResponse> results = noteService.listActive(companyId, contactId, leadId, opportunityId);

        assertEquals(List.of("Newer", "Older"), results.stream().map(NoteResponse::title).toList());
        verify(noteRepository).findActive(companyId, contactId, leadId, opportunityId);
    }

    @Test
    void missingOrArchivedNoteCannotBeReadUpdatedOrArchivedAgain() {
        UUID id = UUID.randomUUID();
        when(noteRepository.findByIdAndArchivedFalse(id)).thenReturn(Optional.empty());
        NoteRequest request = request(UUID.randomUUID(), null, null, null, "Title", "Body");

        assertThrows(NoteNotFoundException.class, () -> noteService.getById(id));
        assertThrows(NoteNotFoundException.class, () -> noteService.update(id, request));
        assertThrows(NoteNotFoundException.class, () -> noteService.archive(id));
        verifyNoInteractions(companyRepository, contactRepository, leadRepository, opportunityRepository);
    }

    @Test
    void activeListUsesRepositoryContractThatExcludesArchivedNotes() {
        when(noteRepository.findActive(null, null, null, null)).thenReturn(List.of());

        assertTrue(noteService.listActive(null, null, null, null).isEmpty());
        verify(noteRepository).findActive(null, null, null, null);
    }

    @Test
    void archiveSoftDeletesAndUpdatesTimestamp() {
        UUID id = UUID.randomUUID();
        Note note = new Note(company(UUID.randomUUID()), null, null, null, "Title", "Body");
        when(noteRepository.findByIdAndArchivedFalse(id)).thenReturn(Optional.of(note));
        when(noteRepository.save(note)).thenReturn(note);

        noteService.archive(id);

        assertTrue(note.isArchived());
        assertNotNull(note.getUpdatedAt());
        verify(noteRepository, never()).delete(any());
    }

    private Company company(UUID id) {
        Company value = mock(Company.class);
        when(value.getId()).thenReturn(id);
        return value;
    }

    private Contact contact(UUID id) {
        Contact value = mock(Contact.class);
        when(value.getId()).thenReturn(id);
        return value;
    }

    private Lead lead(UUID id) {
        Lead value = mock(Lead.class);
        when(value.getId()).thenReturn(id);
        return value;
    }

    private Opportunity opportunity(UUID id) {
        Opportunity value = mock(Opportunity.class);
        when(value.getId()).thenReturn(id);
        return value;
    }

    private NoteRequest request(UUID companyId, UUID contactId, UUID leadId, UUID opportunityId, String title, String body) {
        return new NoteRequest(companyId, contactId, leadId, opportunityId, title, body);
    }
}
