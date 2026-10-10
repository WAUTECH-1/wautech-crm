package com.wautech.crm.note.controller;

import com.wautech.crm.note.dto.NoteRequest;
import com.wautech.crm.note.dto.NoteResponse;
import com.wautech.crm.note.service.NoteService;
import com.wautech.crm.platform.tenant.AuthenticatedOrganizationContext;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/notes")
public class NoteController {
    private final NoteService noteService;
    private final AuthenticatedOrganizationContext organizationContext;

    public NoteController(NoteService noteService, AuthenticatedOrganizationContext organizationContext) {
        this.noteService = noteService;
        this.organizationContext = organizationContext;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NoteResponse create(@Valid @RequestBody NoteRequest request) {
        return noteService.create(organizationContext.requireOrganizationId(), request);
    }

    @GetMapping
    public List<NoteResponse> listActive(@RequestParam(required = false) UUID companyId,
                                         @RequestParam(required = false) UUID contactId,
                                         @RequestParam(required = false) UUID leadId,
                                         @RequestParam(required = false) UUID opportunityId,
                                         @RequestParam(required = false) String search,
                                         @RequestParam(required = false) String sortBy,
                                         @RequestParam(required = false) String sortDirection) {
        return search == null && sortBy == null && sortDirection == null
                ? noteService.listActive(organizationContext.requireOrganizationId(), companyId, contactId, leadId, opportunityId)
                : noteService.listActive(organizationContext.requireOrganizationId(), companyId, contactId, leadId, opportunityId,
                search, sortBy, sortDirection);
    }

    @GetMapping("/{id}")
    public NoteResponse getById(@PathVariable UUID id) {
        return noteService.getById(organizationContext.requireOrganizationId(), id);
    }

    @PutMapping("/{id}")
    public NoteResponse update(@PathVariable UUID id, @Valid @RequestBody NoteRequest request) {
        return noteService.update(organizationContext.requireOrganizationId(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@PathVariable UUID id) {
        noteService.archive(organizationContext.requireOrganizationId(), id);
    }
}
