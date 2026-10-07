package com.wautech.crm.platform.search;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SearchTextTest {
    @Test
    void createsCaseInsensitiveContainsPatternAndEscapesLikeWildcards() {
        assertEquals("%a!!b!%c!_d%", SearchText.containsPattern(" A!B%C_D "));
    }

    @Test
    void blankSearchDoesNotRestrictResults() {
        assertNull(SearchText.containsPattern("  "));
    }
}
