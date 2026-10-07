package com.wautech.crm.opportunity.controller;

import com.wautech.crm.opportunity.dto.OpportunityRequest;
import com.wautech.crm.opportunity.dto.OpportunityResponse;
import com.wautech.crm.opportunity.dto.OpportunityStageRequest;
import com.wautech.crm.opportunity.entity.OpportunityStage;
import com.wautech.crm.opportunity.service.OpportunityService;
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
@RequestMapping("/api/opportunities")
public class OpportunityController {
    private final OpportunityService opportunityService;

    public OpportunityController(OpportunityService opportunityService) {
        this.opportunityService = opportunityService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OpportunityResponse create(@Valid @RequestBody OpportunityRequest request) {
        return opportunityService.create(request);
    }

    @GetMapping
    public List<OpportunityResponse> listActive(@RequestParam(required = false) UUID companyId,
                                                @RequestParam(required = false) UUID contactId,
                                                @RequestParam(required = false) OpportunityStage stage,
                                                @RequestParam(required = false) String search,
                                                @RequestParam(required = false) String sortBy,
                                                @RequestParam(required = false) String sortDirection) {
        return search == null && sortBy == null && sortDirection == null
                ? opportunityService.listActive(companyId, contactId, stage)
                : opportunityService.listActive(companyId, contactId, stage, search, sortBy, sortDirection);
    }

    @GetMapping("/{id}")
    public OpportunityResponse getById(@PathVariable UUID id) {
        return opportunityService.getById(id);
    }

    @PutMapping("/{id}")
    public OpportunityResponse update(@PathVariable UUID id, @Valid @RequestBody OpportunityRequest request) {
        return opportunityService.update(id, request);
    }

    @PatchMapping("/{id}/stage")
    public OpportunityResponse changeStage(@PathVariable UUID id,
                                           @Valid @RequestBody OpportunityStageRequest request) {
        return opportunityService.changeStage(id, request.stage());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@PathVariable UUID id) {
        opportunityService.archive(id);
    }
}
