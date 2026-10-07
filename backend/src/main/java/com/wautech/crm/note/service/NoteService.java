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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class NoteService {
    private final NoteRepository noteRepository;
    private final CompanyRepository companyRepository;
    private final ContactRepository contactRepository;
    private final LeadRepository leadRepository;
    private final OpportunityRepository opportunityRepository;

    public NoteService(NoteRepository noteRepository, CompanyRepository companyRepository,
                       ContactRepository contactRepository, LeadRepository leadRepository,
                       OpportunityRepository opportunityRepository) {
        this.noteRepository = noteRepository;
        this.companyRepository = companyRepository;
        this.contactRepository = contactRepository;
        this.leadRepository = leadRepository;
        this.opportunityRepository = opportunityRepository;
    }

    public NoteResponse create(NoteRequest request) {
        ParentRecords parents = findParents(request);
        Note note = new Note(parents.company(), parents.contact(), parents.lead(), parents.opportunity(),
                request.title().trim(), request.body());
        return NoteResponse.from(noteRepository.save(note));
    }

    @Transactional(readOnly = true)
    public List<NoteResponse> listActive(UUID companyId, UUID contactId, UUID leadId, UUID opportunityId) {
        return noteRepository.findActive(companyId, contactId, leadId, opportunityId)
                .stream().map(NoteResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public NoteResponse getById(UUID id) {
        return NoteResponse.from(findActiveNote(id));
    }

    public NoteResponse update(UUID id, NoteRequest request) {
        Note note = findActiveNote(id);
        ParentRecords parents = findParents(request);
        note.update(parents.company(), parents.contact(), parents.lead(), parents.opportunity(),
                request.title().trim(), request.body());
        return NoteResponse.from(noteRepository.save(note));
    }

    public void archive(UUID id) {
        Note note = findActiveNote(id);
        note.archive();
        noteRepository.save(note);
    }

    private ParentRecords findParents(NoteRequest request) {
        Company company = request.companyId() == null ? null : companyRepository
                .findByIdAndArchivedFalse(request.companyId())
                .orElseThrow(() -> new CompanyNotFoundException(request.companyId()));
        Contact contact = request.contactId() == null ? null : contactRepository
                .findByIdAndArchivedFalse(request.contactId())
                .orElseThrow(() -> new ContactNotFoundException(request.contactId()));
        Lead lead = request.leadId() == null ? null : leadRepository
                .findByIdAndArchivedFalse(request.leadId())
                .orElseThrow(() -> new LeadNotFoundException(request.leadId()));
        Opportunity opportunity = request.opportunityId() == null ? null : opportunityRepository
                .findByIdAndArchivedFalse(request.opportunityId())
                .orElseThrow(() -> new OpportunityNotFoundException(request.opportunityId()));
        return new ParentRecords(company, contact, lead, opportunity);
    }

    private Note findActiveNote(UUID id) {
        return noteRepository.findByIdAndArchivedFalse(id).orElseThrow(() -> new NoteNotFoundException(id));
    }

    private record ParentRecords(Company company, Contact contact, Lead lead, Opportunity opportunity) {
    }
}
