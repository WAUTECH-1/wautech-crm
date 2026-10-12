package com.wautech.crm.organization.dto;
import com.wautech.crm.organization.entity.OrganizationRole;
import java.util.UUID;
/** Only the authenticated caller's own active membership. */
public record CurrentUserOrganizationResponse(UUID organizationId, String organizationName,
        UUID membershipId, OrganizationRole role, String status) { }
