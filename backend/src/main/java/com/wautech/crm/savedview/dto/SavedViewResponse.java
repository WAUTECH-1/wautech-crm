package com.wautech.crm.savedview.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.wautech.crm.savedview.entity.SavedView;
import com.wautech.crm.savedview.entity.SavedViewResource;

import java.time.Instant;
import java.util.UUID;

public record SavedViewResponse(UUID id, String name, SavedViewResource resource, int configurationVersion,
                                JsonNode configuration, Instant createdAt, Instant updatedAt) {
    public static SavedViewResponse from(SavedView view) {
        return new SavedViewResponse(view.getId(), view.getName(), view.getResource(),
                view.getConfigurationVersion(), view.getConfiguration(), view.getCreatedAt(), view.getUpdatedAt());
    }
}
