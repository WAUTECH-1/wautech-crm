package com.wautech.crm.organization.repository;

import com.wautech.crm.organization.entity.MembershipStatus;
import com.wautech.crm.organization.entity.OrganizationMembership;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationMembershipRepository extends JpaRepository<OrganizationMembership, UUID> {
    List<OrganizationMembership> findAllByOrganization_IdOrderByCreatedAtDesc(UUID organizationId);
    List<OrganizationMembership> findAllByOrganization_IdAndUser_IdOrderByCreatedAtDesc(UUID organizationId, UUID userId);
    Optional<OrganizationMembership> findByIdAndOrganization_Id(UUID id, UUID organizationId);
    boolean existsByOrganization_IdAndUser_Id(UUID organizationId, UUID userId);
    boolean existsByOrganization_IdAndUser_IdAndStatus(UUID organizationId, UUID userId, MembershipStatus status);
}
