package com.wautech.crm.activity.controller;

import com.wautech.crm.activity.dto.ActivityRequest;
import com.wautech.crm.activity.dto.ActivityResponse;
import com.wautech.crm.activity.entity.ActivityType;
import com.wautech.crm.activity.service.ActivityService;
import com.wautech.crm.platform.tenant.AuthenticatedOrganizationContext;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/activities")
public class ActivityController {
    private final ActivityService activityService;
    private final AuthenticatedOrganizationContext organizationContext;

    public ActivityController(ActivityService activityService, AuthenticatedOrganizationContext organizationContext) {
        this.activityService = activityService;
        this.organizationContext = organizationContext;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ActivityResponse create(@Valid @RequestBody ActivityRequest request) {
        return activityService.create(organizationContext.requireOrganizationId(), request);
    }

    @GetMapping
    public List<ActivityResponse> listActive(@RequestParam(required = false) UUID companyId,
                                             @RequestParam(required = false) UUID contactId,
                                             @RequestParam(required = false) UUID leadId,
                                             @RequestParam(required = false) UUID opportunityId,
                                             @RequestParam(required = false) ActivityType type,
                                             @RequestParam(required = false) String search,
                                             @RequestParam(required = false) String sortBy,
                                             @RequestParam(required = false) String sortDirection) {
        return search == null && sortBy == null && sortDirection == null
                ? activityService.listActive(organizationContext.requireOrganizationId(), companyId, contactId, leadId, opportunityId, type)
                : activityService.listActive(organizationContext.requireOrganizationId(), companyId, contactId, leadId, opportunityId, type,
                search, sortBy, sortDirection);
    }

    @GetMapping("/{id}")
    public ActivityResponse getById(@PathVariable UUID id) {
        return activityService.getById(organizationContext.requireOrganizationId(), id);
    }

    @PutMapping("/{id}")
    public ActivityResponse update(@PathVariable UUID id, @Valid @RequestBody ActivityRequest request) {
        return activityService.update(organizationContext.requireOrganizationId(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@PathVariable UUID id) {
        activityService.archive(organizationContext.requireOrganizationId(), id);
    }
}
