package com.wautech.crm.lead.service;

import com.wautech.crm.company.entity.Company;
import com.wautech.crm.company.repository.CompanyRepository;
import com.wautech.crm.company.service.CompanyNotFoundException;
import com.wautech.crm.lead.dto.LeadRequest;
import com.wautech.crm.lead.dto.LeadResponse;
import com.wautech.crm.lead.entity.Lead;
import com.wautech.crm.lead.entity.LeadStatus;
import com.wautech.crm.lead.repository.LeadRepository;
import com.wautech.crm.platform.search.ListSort;
import com.wautech.crm.platform.search.SearchText;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.util.Map;
import java.util.function.Function;

@Service
@Transactional
public class LeadService {
    private final LeadRepository leadRepository;
    private final CompanyRepository companyRepository;

    public LeadService(LeadRepository leadRepository, CompanyRepository companyRepository) {
        this.leadRepository = leadRepository;
        this.companyRepository = companyRepository;
    }

    public LeadResponse create(LeadRequest request) {
        Company company = request.companyId() == null ? null : findActiveCompany(request.companyId());
        Lead lead = new Lead(company, request.firstName().trim(), request.lastName().trim(),
                request.email(), request.phone(), request.jobTitle());
        return LeadResponse.from(leadRepository.save(lead));
    }

    @Transactional(readOnly = true)
    public List<LeadResponse> listActive(LeadStatus status, UUID companyId) {
        List<Lead> leads;
        if (status != null && companyId != null) {
            leads = leadRepository.findAllByCompany_IdAndArchivedFalseAndStatusOrderByCreatedAtDesc(companyId, status);
        } else if (status != null) leads = leadRepository.findAllByArchivedFalseAndStatusOrderByCreatedAtDesc(status);
        else if (companyId != null) leads = leadRepository.findAllByCompany_IdAndArchivedFalseOrderByCreatedAtDesc(companyId);
        else leads = leadRepository.findAllByArchivedFalseOrderByCreatedAtDesc();
        return leads.stream().map(LeadResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<LeadResponse> listActive(LeadStatus status, UUID companyId, String search,
                                         String sortBy, String sortDirection) {
        List<Lead> leads = new ArrayList<>(leadRepository.findActive(companyId, status, SearchText.containsPattern(search)));
        ListSort.apply(leads, sortBy, sortDirection, SORT_FIELDS, Lead::getId);
        return leads.stream().map(LeadResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public LeadResponse getById(UUID id) {
        return LeadResponse.from(findActiveLead(id));
    }

    public LeadResponse update(UUID id, LeadRequest request) {
        Lead lead = findActiveLead(id);
        Company company = request.companyId() == null ? null : findActiveCompany(request.companyId());
        lead.update(company, request.firstName().trim(), request.lastName().trim(), request.email(),
                request.phone(), request.jobTitle());
        return LeadResponse.from(leadRepository.save(lead));
    }

    public LeadResponse changeStatus(UUID id, LeadStatus newStatus) {
        Lead lead = findActiveLead(id);
        lead.changeStatus(newStatus);
        return LeadResponse.from(leadRepository.save(lead));
    }

    public void archive(UUID id) {
        Lead lead = findActiveLead(id);
        lead.archive();
        leadRepository.save(lead);
    }

    private Company findActiveCompany(UUID id) {
        return companyRepository.findByIdAndArchivedFalse(id).orElseThrow(() -> new CompanyNotFoundException(id));
    }

    private Lead findActiveLead(UUID id) {
        return leadRepository.findByIdAndArchivedFalse(id).orElseThrow(() -> new LeadNotFoundException(id));
    }

    private static final Map<String, Function<Lead, Comparable<?>>> SORT_FIELDS = Map.ofEntries(
            Map.entry("id", Lead::getId), Map.entry("firstName", Lead::getFirstName),
            Map.entry("lastName", Lead::getLastName), Map.entry("email", Lead::getEmail),
            Map.entry("phone", Lead::getPhone), Map.entry("jobTitle", Lead::getJobTitle),
            Map.entry("status", l -> l.getStatus().name()),
            Map.entry("createdAt", Lead::getCreatedAt), Map.entry("updatedAt", Lead::getUpdatedAt));
}
