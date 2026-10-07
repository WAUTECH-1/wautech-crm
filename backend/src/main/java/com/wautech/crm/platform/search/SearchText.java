package com.wautech.crm.platform.search;

import java.util.Locale;

public final class SearchText {
    private SearchText() {
    }

    public static String containsPattern(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String escaped = search.trim().toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }
}
