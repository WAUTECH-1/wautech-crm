package com.wautech.crm.savedview.service;

import com.wautech.crm.audit.AuditedMutation;
import com.fasterxml.jackson.databind.JsonNode;
import com.wautech.crm.savedview.dto.SavedViewRequest;
import com.wautech.crm.savedview.dto.SavedViewResponse;
import com.wautech.crm.savedview.entity.SavedView;
import com.wautech.crm.savedview.repository.SavedViewRepository;
import com.wautech.crm.organization.entity.Organization;
import com.wautech.crm.organization.service.OrganizationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class SavedViewService {
    private final SavedViewRepository repository;
    private final SavedViewConfigurationValidator validator;
    private final OrganizationService organizationService;
    public SavedViewService(SavedViewRepository repository, SavedViewConfigurationValidator validator,
                            OrganizationService organizationService) {
        this.repository = repository;
        this.validator = validator;
        this.organizationService = organizationService;
    }

    @PreAuthorize("@crmAuthorization.canWrite(#p0)")
    @AuditedMutation(eventType = "SAVED_VIEW_CREATED", targetType = "SAVED_VIEW")
    public SavedViewResponse create(UUID organizationId, SavedViewRequest request) {
        Organization organization = organizationService.requireActiveOrganization(organizationId);
        validate(request);
        JsonNode configuration = request.configuration().deepCopy();
        SavedView view = new SavedView(organization, request.name().trim(), request.resource(), request.configurationVersion(), configuration);
        return SavedViewResponse.from(repository.save(view));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@crmAuthorization.canView(#p0)")
    public List<SavedViewResponse> listActive(UUID organizationId) {
        organizationService.requireActiveOrganization(organizationId);
        return repository.findAllByOrganization_IdAndArchivedFalseOrderByNameAscIdAsc(organizationId).stream()
                .map(SavedViewResponse::from).toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("@crmAuthorization.canView(#p0)")
    public SavedViewResponse getById(UUID organizationId, UUID id) {
        organizationService.requireActiveOrganization(organizationId);
        return SavedViewResponse.from(findActive(organizationId, id));
    }

    @PreAuthorize("@crmAuthorization.canWrite(#p0)")
    @AuditedMutation(eventType = "SAVED_VIEW_UPDATED", targetType = "SAVED_VIEW")
    public SavedViewResponse update(UUID organizationId, UUID id, SavedViewRequest request) {
        organizationService.requireActiveOrganization(organizationId);
        validate(request);
        SavedView view = findActive(organizationId, id);
        view.update(request.name().trim(), request.resource(), request.configurationVersion(),
                request.configuration().deepCopy());
        return SavedViewResponse.from(repository.save(view));
    }

    @PreAuthorize("@crmAuthorization.canWrite(#p0)")
    @AuditedMutation(eventType = "SAVED_VIEW_ARCHIVED", targetType = "SAVED_VIEW")
    public void archive(UUID organizationId, UUID id) {
        organizationService.requireActiveOrganization(organizationId);
        SavedView view = findActive(organizationId, id);
        view.archive();
        repository.save(view);
    }

    private void validate(SavedViewRequest request) {
        validator.validate(request.resource(), request.configurationVersion(), request.configuration());
    }

    private SavedView findActive(UUID organizationId, UUID id) {
        return repository.findByIdAndOrganization_IdAndArchivedFalse(id, organizationId)
                .orElseThrow(() -> new SavedViewNotFoundException(id));
    }
}
