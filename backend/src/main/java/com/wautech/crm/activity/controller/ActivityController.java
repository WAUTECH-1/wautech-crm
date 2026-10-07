package com.wautech.crm.activity.controller;

import com.wautech.crm.activity.dto.ActivityRequest;
import com.wautech.crm.activity.dto.ActivityResponse;
import com.wautech.crm.activity.entity.ActivityType;
import com.wautech.crm.activity.service.ActivityService;
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

    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ActivityResponse create(@Valid @RequestBody ActivityRequest request) {
        return activityService.create(request);
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
                ? activityService.listActive(companyId, contactId, leadId, opportunityId, type)
                : activityService.listActive(companyId, contactId, leadId, opportunityId, type,
                search, sortBy, sortDirection);
    }

    @GetMapping("/{id}")
    public ActivityResponse getById(@PathVariable UUID id) {
        return activityService.getById(id);
    }

    @PutMapping("/{id}")
    public ActivityResponse update(@PathVariable UUID id, @Valid @RequestBody ActivityRequest request) {
        return activityService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@PathVariable UUID id) {
        activityService.archive(id);
    }
}
