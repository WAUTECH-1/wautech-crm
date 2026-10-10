package com.wautech.crm.company.controller;

import com.wautech.crm.company.dto.CompanyRequest;
import com.wautech.crm.company.dto.CompanyResponse;
import com.wautech.crm.company.service.CompanyService;
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
@RequestMapping("/api/companies")
public class CompanyController {
    private final CompanyService companyService;
    private final AuthenticatedOrganizationContext organizationContext;

    public CompanyController(CompanyService companyService, AuthenticatedOrganizationContext organizationContext) {
        this.companyService = companyService;
        this.organizationContext = organizationContext;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CompanyResponse create(@Valid @RequestBody CompanyRequest request) {
        return companyService.create(organizationContext.requireOrganizationId(), request);
    }

    @GetMapping
    public List<CompanyResponse> listActive(@RequestParam(required = false) String search,
                                            @RequestParam(required = false) String sortBy,
                                            @RequestParam(required = false) String sortDirection) {
        return search == null && sortBy == null && sortDirection == null
                ? companyService.listActive(organizationContext.requireOrganizationId())
                : companyService.listActive(organizationContext.requireOrganizationId(), search, sortBy, sortDirection);
    }

    @GetMapping("/{id}")
    public CompanyResponse getById(@PathVariable UUID id) {
        return companyService.getById(organizationContext.requireOrganizationId(), id);
    }

    @PutMapping("/{id}")
    public CompanyResponse update(@PathVariable UUID id, @Valid @RequestBody CompanyRequest request) {
        return companyService.update(organizationContext.requireOrganizationId(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@PathVariable UUID id) {
        companyService.archive(organizationContext.requireOrganizationId(), id);
    }
}
