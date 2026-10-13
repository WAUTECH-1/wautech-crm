package com.wautech.crm.organization.controller;

import com.wautech.crm.organization.dto.*;
import com.wautech.crm.organization.service.OrganizationService;
import com.wautech.crm.organization.service.OrganizationMembershipService;
import com.wautech.crm.platform.tenant.AuthenticatedOrganizationContext;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

/** Current tenant only. Existing service method security is authoritative. */
@RestController
@RequestMapping("/api/organization")
public class OrganizationAdministrationController {
    private final AuthenticatedOrganizationContext context;
    private final OrganizationService organizations;
    private final OrganizationMembershipService memberships;
    public OrganizationAdministrationController(AuthenticatedOrganizationContext context,
            OrganizationService organizations, OrganizationMembershipService memberships) {
        this.context = context;
        this.organizations = organizations;
        this.memberships = memberships;
    }
    @GetMapping public OrganizationResponse profile() {
        return organizations.getById(context.requireOrganizationId());
    }
    @PatchMapping public OrganizationResponse update(@Valid @RequestBody OrganizationRequest request) {
        return organizations.update(context.requireOrganizationId(), request);
    }
    @GetMapping("/memberships") public List<OrganizationMembershipResponse> members() {
        return memberships.listByOrganization(context.requireOrganizationId());
    }
    /** Creates INVITED/VIEWER membership for an existing user. Does not deliver email. */
    @PostMapping("/memberships") @ResponseStatus(HttpStatus.CREATED)
    public OrganizationMembershipResponse create(@Valid @RequestBody OrganizationMembershipCreateRequest request) {
        return memberships.create(context.requireOrganizationId(), request.userId());
    }
    @PatchMapping("/memberships/{id}/status")
    public OrganizationMembershipResponse status(@PathVariable UUID id, @Valid @RequestBody MembershipStatusRequest request) {
        return memberships.transition(context.requireOrganizationId(), id, request.status());
    }
    @PatchMapping("/memberships/{id}/role")
    public OrganizationMembershipResponse role(@PathVariable UUID id, @Valid @RequestBody MembershipRoleRequest request) {
        return memberships.changeRole(context.requireOrganizationId(), id, request.role());
    }
}
