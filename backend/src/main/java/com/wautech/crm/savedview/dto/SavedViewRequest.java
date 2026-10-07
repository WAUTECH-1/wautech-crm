package com.wautech.crm.savedview.dto;

import com.wautech.crm.savedview.entity.SavedViewResource;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SavedViewRequest(
        @NotBlank @Size(max = 200) String name,
        @NotNull SavedViewResource resource,
        @NotNull Integer configurationVersion,
        @NotNull JsonNode configuration
) {
}
