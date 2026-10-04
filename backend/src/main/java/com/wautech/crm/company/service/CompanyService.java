package com.wautech.crm.company.service;

import com.wautech.crm.company.dto.CompanyRequest;
import com.wautech.crm.company.dto.CompanyResponse;
import com.wautech.crm.company.entity.Company;
import com.wautech.crm.company.repository.CompanyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class CompanyService {
    private final CompanyRepository companyRepository;

    public CompanyService(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }

    public CompanyResponse create(CompanyRequest request) {
        Company company = new Company(request.name().trim(), request.website(), request.industry(),
                request.phone(), request.email(), request.resolvedStatus());
        return CompanyResponse.from(companyRepository.save(company));
    }

    @Transactional(readOnly = true)
    public List<CompanyResponse> listActive() {
        return companyRepository.findAllByArchivedFalseOrderByCreatedAtDesc().stream()
                .map(CompanyResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CompanyResponse getById(UUID id) {
        return CompanyResponse.from(findActiveCompany(id));
    }

    public CompanyResponse update(UUID id, CompanyRequest request) {
        Company company = findActiveCompany(id);
        company.update(request.name().trim(), request.website(), request.industry(), request.phone(),
                request.email(), request.resolvedStatus());
        return CompanyResponse.from(companyRepository.save(company));
    }

    public void archive(UUID id) {
        Company company = findActiveCompany(id);
        company.archive();
        companyRepository.save(company);
    }

    private Company findActiveCompany(UUID id) {
        return companyRepository.findByIdAndArchivedFalse(id).orElseThrow(() -> new CompanyNotFoundException(id));
    }
}
