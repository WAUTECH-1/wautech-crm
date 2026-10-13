package com.wautech.crm.organization.service;
import com.wautech.crm.identity.security.CrmUserPrincipal;
import com.wautech.crm.identity.service.UserService;
import com.wautech.crm.organization.dto.CurrentUserOrganizationResponse;
import com.wautech.crm.organization.entity.MembershipStatus;
import com.wautech.crm.organization.repository.OrganizationMembershipRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;
@Service
@Transactional(readOnly = true)
public class CurrentUserOrganizationService {
    private final OrganizationMembershipRepository repository;
    private final UserService users;
    public CurrentUserOrganizationService(OrganizationMembershipRepository repository, UserService users) {
        this.repository = repository;
        this.users = users;
    }
    public List<CurrentUserOrganizationResponse> list() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof CrmUserPrincipal principal)) {
            throw new AuthenticationCredentialsNotFoundException("Authentication is required");
        }
        users.requireEnabledUser(principal.getId());
        return repository.findAllByUser_IdAndStatusAndOrganization_ArchivedFalseOrderByOrganization_NameAsc(
                principal.getId(), MembershipStatus.ACTIVE).stream()
                .map(m -> new CurrentUserOrganizationResponse(m.getOrganization().getId(),
                        m.getOrganization().getName(), m.getId(), m.getRole(), "ACTIVE")).toList();
    }
    public CurrentUserOrganizationResponse membership(UUID organizationId) {
        return list().stream().filter(m -> m.organizationId().equals(organizationId)).findFirst()
                .orElseThrow(() -> new AccessDeniedException("Organization access is not permitted"));
    }
}
