package com.wautech.crm.organization.repository;

import com.wautech.crm.organization.entity.Organization;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {
    Optional<Organization> findByIdAndArchivedFalse(UUID id);
    boolean existsByIdAndArchivedFalse(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Organization o where o.id = :id and o.archived = false")
    Optional<Organization> lockByIdAndArchivedFalse(@Param("id") UUID id);
}
