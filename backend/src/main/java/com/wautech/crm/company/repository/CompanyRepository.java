package com.wautech.crm.company.repository;

import com.wautech.crm.company.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompanyRepository extends JpaRepository<Company, UUID> {
    List<Company> findAllByArchivedFalseOrderByCreatedAtDesc();
    @Query("select c from Company c where c.archived = false and " +
            "(:search is null or lower(c.name) like :search escape '!' or lower(c.industry) like :search escape '!' " +
            "or lower(c.website) like :search escape '!' or lower(c.email) like :search escape '!' " +
            "or lower(c.phone) like :search escape '!') order by c.createdAt desc")
    List<Company> findActive(@Param("search") String search);
    Optional<Company> findByIdAndArchivedFalse(UUID id);
}
