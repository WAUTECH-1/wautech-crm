package com.wautech.crm.company.repository;

import com.wautech.crm.company.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompanyRepository extends JpaRepository<Company, UUID> {
    List<Company> findAllByArchivedFalseOrderByCreatedAtDesc();
    Optional<Company> findByIdAndArchivedFalse(UUID id);
}
