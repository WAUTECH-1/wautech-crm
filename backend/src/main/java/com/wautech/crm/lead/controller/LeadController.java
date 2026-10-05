package com.wautech.crm.lead.controller;

import com.wautech.crm.lead.dto.LeadRequest;
import com.wautech.crm.lead.dto.LeadResponse;
import com.wautech.crm.lead.dto.LeadStatusRequest;
import com.wautech.crm.lead.entity.LeadStatus;
import com.wautech.crm.lead.service.LeadService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
@RequestMapping("/api/leads")
public class LeadController {
    private final LeadService leadService;

    public LeadController(LeadService leadService) {
        this.leadService = leadService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LeadResponse create(@Valid @RequestBody LeadRequest request) {
        return leadService.create(request);
    }

    @GetMapping
    public List<LeadResponse> listActive(@RequestParam(required = false) LeadStatus status,
                                         @RequestParam(required = false) UUID companyId) {
        return leadService.listActive(status, companyId);
    }

    @GetMapping("/{id}")
    public LeadResponse getById(@PathVariable UUID id) {
        return leadService.getById(id);
    }

    @PutMapping("/{id}")
    public LeadResponse update(@PathVariable UUID id, @Valid @RequestBody LeadRequest request) {
        return leadService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public LeadResponse changeStatus(@PathVariable UUID id, @Valid @RequestBody LeadStatusRequest request) {
        return leadService.changeStatus(id, request.status());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@PathVariable UUID id) {
        leadService.archive(id);
    }
}
