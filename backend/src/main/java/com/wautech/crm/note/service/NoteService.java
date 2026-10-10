package com.wautech.crm.note.service;

import com.wautech.crm.audit.AuditedMutation;
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
import com.wautech.crm.platform.search.ListSort;
import com.wautech.crm.platform.search.SearchText;
import com.wautech.crm.organization.entity.Organization;
import com.wautech.crm.organization.service.OrganizationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.util.Map;
import java.util.function.Function;

@Service
@Transactional
public class NoteService {
    private final NoteRepository noteRepository;
    private final CompanyRepository companyRepository;
    private final ContactRepository contactRepository;
    private final LeadRepository leadRepository;
    private final OpportunityRepository opportunityRepository;
    private final OrganizationService organizationService;

    public NoteService(NoteRepository noteRepository, CompanyRepository companyRepository,
                       ContactRepository contactRepository, LeadRepository leadRepository,
                       OpportunityRepository opportunityRepository, OrganizationService organizationService) {
        this.noteRepository = noteRepository;
        this.companyRepository = companyRepository;
        this.contactRepository = contactRepository;
        this.leadRepository = leadRepository;
        this.opportunityRepository = opportunityRepository;
        this.organizationService = organizationService;
    }

    @PreAuthorize("@crmAuthorization.canWrite(#p0)")
    @AuditedMutation(eventType = "NOTE_CREATED", targetType = "NOTE")
    public NoteResponse create(UUID organizationId, NoteRequest request) {
        Organization organization = organizationService.requireActiveOrganization(organizationId);
        ParentRecords parents = findParents(organizationId, request);
        Note note = new Note(organization, parents.company(), parents.contact(), parents.lead(), parents.opportunity(),
                request.title().trim(), request.body());
        return NoteResponse.from(noteRepository.save(note));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@crmAuthorization.canView(#p0)")
    public List<NoteResponse> listActive(UUID organizationId, UUID companyId, UUID contactId, UUID leadId, UUID opportunityId) {
        organizationService.requireActiveOrganization(organizationId);
        return noteRepository.findActive(organizationId, companyId, contactId, leadId, opportunityId)
                .stream().map(NoteResponse::from).toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@crmAuthorization.canView(#p0)")
    public List<NoteResponse> listActive(UUID organizationId, UUID companyId, UUID contactId, UUID leadId, UUID opportunityId,
                                        String search, String sortBy, String sortDirection) {
        organizationService.requireActiveOrganization(organizationId);
        List<Note> rows = new ArrayList<>(noteRepository.findActive(organizationId, companyId, contactId, leadId, opportunityId,
                SearchText.containsPattern(search)));
        ListSort.apply(rows, sortBy, sortDirection, SORT_FIELDS, Note::getId);
        return rows.stream().map(NoteResponse::from).toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@crmAuthorization.canView(#p0)")
    public NoteResponse getById(UUID organizationId, UUID id) {
        organizationService.requireActiveOrganization(organizationId);
        return NoteResponse.from(findActiveNote(organizationId, id));
    }

    @PreAuthorize("@crmAuthorization.canWrite(#p0)")
    @AuditedMutation(eventType = "NOTE_UPDATED", targetType = "NOTE")
    public NoteResponse update(UUID organizationId, UUID id, NoteRequest request) {
        organizationService.requireActiveOrganization(organizationId);
        Note note = findActiveNote(organizationId, id);
        ParentRecords parents = findParents(organizationId, request);
        note.update(parents.company(), parents.contact(), parents.lead(), parents.opportunity(),
                request.title().trim(), request.body());
        return NoteResponse.from(noteRepository.save(note));
    }

    @PreAuthorize("@crmAuthorization.canWrite(#p0)")
    @AuditedMutation(eventType = "NOTE_ARCHIVED", targetType = "NOTE")
    public void archive(UUID organizationId, UUID id) {
        organizationService.requireActiveOrganization(organizationId);
        Note note = findActiveNote(organizationId, id);
        note.archive();
        noteRepository.save(note);
    }

    private ParentRecords findParents(UUID organizationId, NoteRequest request) {
        Company company = request.companyId() == null ? null : companyRepository
                .findByIdAndOrganization_IdAndArchivedFalse(request.companyId(), organizationId)
                .orElseThrow(() -> new CompanyNotFoundException(request.companyId()));
        Contact contact = request.contactId() == null ? null : contactRepository
                .findByIdAndOrganization_IdAndArchivedFalse(request.contactId(), organizationId)
                .orElseThrow(() -> new ContactNotFoundException(request.contactId()));
        Lead lead = request.leadId() == null ? null : leadRepository
                .findByIdAndOrganization_IdAndArchivedFalse(request.leadId(), organizationId)
                .orElseThrow(() -> new LeadNotFoundException(request.leadId()));
        Opportunity opportunity = request.opportunityId() == null ? null : opportunityRepository
                .findByIdAndOrganization_IdAndArchivedFalse(request.opportunityId(), organizationId)
                .orElseThrow(() -> new OpportunityNotFoundException(request.opportunityId()));
        return new ParentRecords(company, contact, lead, opportunity);
    }

    private Note findActiveNote(UUID organizationId, UUID id) {
        return noteRepository.findByIdAndOrganization_IdAndArchivedFalse(id, organizationId)
                .orElseThrow(() -> new NoteNotFoundException(id));
    }

    private record ParentRecords(Company company, Contact contact, Lead lead, Opportunity opportunity) {
    }

    private static final Map<String, Function<Note, Comparable<?>>> SORT_FIELDS = Map.ofEntries(
            Map.entry("id", Note::getId), Map.entry("title", Note::getTitle),
            Map.entry("createdAt", Note::getCreatedAt), Map.entry("updatedAt", Note::getUpdatedAt));
}
