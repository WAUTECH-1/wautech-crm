package com.wautech.crm.organization.repository;

import com.wautech.crm.organization.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {
    Optional<Organization> findByIdAndArchivedFalse(UUID id);
}
