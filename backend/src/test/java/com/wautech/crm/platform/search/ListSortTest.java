package com.wautech.crm.platform.search;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ListSortTest {
    private record Row(UUID id, String name) { }

    private static final Map<String, Function<Row, Comparable<?>>> FIELDS = Map.of("name", Row::name, "id", Row::id);

    @Test
    void sortsWhitelistedFieldInBothDirectionsAndBreaksTiesById() {
        Row a = new Row(UUID.fromString("00000000-0000-0000-0000-000000000001"), "same");
        Row b = new Row(UUID.fromString("00000000-0000-0000-0000-000000000002"), "same");
        List<Row> rows = new ArrayList<>(List.of(b, a));
        ListSort.apply(rows, "name", "asc", FIELDS, Row::id);
        assertEquals(List.of(a, b), rows);

        rows = new ArrayList<>(List.of(a, b));
        ListSort.apply(rows, "id", "desc", FIELDS, Row::id);
        assertEquals(List.of(b, a), rows);
    }

    @Test
    void rejectsUnsupportedFieldsAndDirectionWithoutSortField() {
        List<Row> rows = new ArrayList<>();
        assertThrows(InvalidListQueryException.class, () -> ListSort.apply(rows, "arbitrary", "asc", FIELDS, Row::id));
        assertThrows(InvalidListQueryException.class, () -> ListSort.apply(rows, null, "asc", FIELDS, Row::id));
        assertThrows(InvalidListQueryException.class, () -> ListSort.apply(rows, "name", "sideways", FIELDS, Row::id));
    }
}
