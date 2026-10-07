package com.wautech.crm.savedview.dto;

import java.util.Map;

public record SavedViewConfiguration(String search, Map<String, String> filters,
                                     String sortBy, String sortDirection) {
    public SavedViewConfiguration {
        filters = filters == null ? Map.of() : Map.copyOf(filters);
    }
}
