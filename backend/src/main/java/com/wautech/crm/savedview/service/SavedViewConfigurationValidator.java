package com.wautech.crm.savedview.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.wautech.crm.platform.search.InvalidListQueryException;
import com.wautech.crm.savedview.dto.SavedViewConfiguration;
import com.wautech.crm.savedview.entity.SavedViewResource;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class SavedViewConfigurationValidator {
    private static final int CURRENT_VERSION = 1;
    private static final Map<SavedViewResource, Set<String>> SORT_FIELDS = Map.of(
            SavedViewResource.COMPANY, Set.of("id", "name", "website", "industry", "phone", "email", "status", "createdAt", "updatedAt"),
            SavedViewResource.CONTACT, Set.of("id", "firstName", "lastName", "email", "phone", "jobTitle", "status", "createdAt", "updatedAt"),
            SavedViewResource.LEAD, Set.of("id", "firstName", "lastName", "email", "phone", "jobTitle", "status", "createdAt", "updatedAt"),
            SavedViewResource.OPPORTUNITY, Set.of("id", "name", "amount", "currency", "stage", "expectedCloseDate", "createdAt", "updatedAt"),
            SavedViewResource.ACTIVITY, Set.of("id", "type", "subject", "occurredAt", "createdAt", "updatedAt"),
            SavedViewResource.TASK, Set.of("id", "title", "status", "priority", "dueAt", "createdAt", "updatedAt"),
            SavedViewResource.NOTE, Set.of("id", "title", "createdAt", "updatedAt"));
    private static final Map<SavedViewResource, Set<String>> FILTERS = Map.of(
            SavedViewResource.COMPANY, Set.of(),
            SavedViewResource.CONTACT, Set.of("companyId"),
            SavedViewResource.LEAD, Set.of("companyId", "status"),
            SavedViewResource.OPPORTUNITY, Set.of("companyId", "contactId", "stage"),
            SavedViewResource.ACTIVITY, Set.of("companyId", "contactId", "leadId", "opportunityId", "type"),
            SavedViewResource.TASK, Set.of("companyId", "contactId", "leadId", "opportunityId", "status", "priority", "dueBefore", "dueAfter", "overdue"),
            SavedViewResource.NOTE, Set.of("companyId", "contactId", "leadId", "opportunityId"));

    public void validate(SavedViewResource resource, int version, JsonNode rawConfiguration) {
        if (version != CURRENT_VERSION) throw new InvalidListQueryException("Unsupported configurationVersion");
        if (resource == null || rawConfiguration == null || !rawConfiguration.isObject()) {
            throw new InvalidListQueryException("resource and object configuration are required");
        }
        var allowedKeys = Set.of("search", "filters", "sortBy", "sortDirection");
        rawConfiguration.fieldNames().forEachRemaining(key -> {
            if (!allowedKeys.contains(key)) throw new InvalidListQueryException("Unsupported configuration field: " + key);
        });
        JsonNode searchNode = rawConfiguration.get("search");
        String search = searchNode == null || searchNode.isNull() ? null : textual(searchNode, "search");
        JsonNode sortByNode = rawConfiguration.get("sortBy");
        String sortBy = sortByNode == null || sortByNode.isNull() ? null : textual(sortByNode, "sortBy");
        JsonNode directionNode = rawConfiguration.get("sortDirection");
        String sortDirection = directionNode == null || directionNode.isNull() ? null : textual(directionNode, "sortDirection");
        JsonNode filtersNode = rawConfiguration.get("filters");
        if (filtersNode != null && !filtersNode.isNull() && !filtersNode.isObject()) {
            throw new InvalidListQueryException("filters must be an object");
        }
        Map<String, String> filters = new java.util.LinkedHashMap<>();
        if (filtersNode != null && filtersNode.isObject()) {
            filtersNode.fields().forEachRemaining(entry -> filters.put(entry.getKey(), textual(entry.getValue(), entry.getKey())));
        }
        SavedViewConfiguration configuration = new SavedViewConfiguration(search, filters, sortBy, sortDirection);
        if (configuration.search() != null && configuration.search().isBlank()) {
            throw new InvalidListQueryException("search must not be blank when supplied");
        }
        if (configuration.sortBy() != null && !SORT_FIELDS.get(resource).contains(configuration.sortBy())) {
            throw new InvalidListQueryException("Unsupported sortBy field for " + resource);
        }
        if (configuration.sortDirection() != null && !configuration.sortDirection().equalsIgnoreCase("asc")
                && !configuration.sortDirection().equalsIgnoreCase("desc")) {
            throw new InvalidListQueryException("sortDirection must be asc or desc");
        }
        if (configuration.sortBy() == null && configuration.sortDirection() != null) {
            throw new InvalidListQueryException("sortDirection requires sortBy");
        }
        for (var filter : configuration.filters().entrySet()) {
            if (!FILTERS.get(resource).contains(filter.getKey())) {
                throw new InvalidListQueryException("Unsupported filter for " + resource + ": " + filter.getKey());
            }
            validateFilterValue(resource, filter.getKey(), filter.getValue());
        }
    }

    private String textual(JsonNode node, String key) {
        if (!node.isTextual()) throw new InvalidListQueryException(key + " must be a string");
        return node.asText();
    }

    private void validateFilterValue(SavedViewResource resource, String key, String value) {
        if (value == null || value.isBlank()) throw new InvalidListQueryException("Filter values must not be blank");
        try {
            if (key.endsWith("Id")) UUID.fromString(value);
            else if (key.equals("dueBefore") || key.equals("dueAfter")) Instant.parse(value);
            else if (key.equals("overdue")) {
                if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) throw new IllegalArgumentException();
            } else if ((resource == SavedViewResource.LEAD && key.equals("status"))) {
                com.wautech.crm.lead.entity.LeadStatus.valueOf(value);
            } else if (resource == SavedViewResource.OPPORTUNITY && key.equals("stage")) {
                com.wautech.crm.opportunity.entity.OpportunityStage.valueOf(value);
            } else if (resource == SavedViewResource.ACTIVITY && key.equals("type")) {
                com.wautech.crm.activity.entity.ActivityType.valueOf(value);
            } else if (resource == SavedViewResource.TASK && key.equals("status")) {
                com.wautech.crm.task.entity.TaskStatus.valueOf(value);
            } else if (resource == SavedViewResource.TASK && key.equals("priority")) {
                com.wautech.crm.task.entity.TaskPriority.valueOf(value);
            }
        } catch (RuntimeException exception) {
            throw new InvalidListQueryException("Invalid value for filter: " + key);
        }
    }
}
