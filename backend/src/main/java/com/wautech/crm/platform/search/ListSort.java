package com.wautech.crm.platform.search;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

public final class ListSort {
    private ListSort() {
    }

    public static <T> void apply(List<T> rows, String sortBy, String sortDirection,
                                 Map<String, Function<T, Comparable<?>>> fields,
                                 Function<T, UUID> id) {
        if (sortBy == null) {
            if (sortDirection != null) {
                throw new InvalidListQueryException("sortDirection requires sortBy");
            }
            return;
        }
        Function<T, Comparable<?>> field = fields.get(sortBy);
        if (field == null) {
            throw new InvalidListQueryException("Unsupported sortBy field: " + sortBy);
        }
        boolean descending;
        if (sortDirection == null || sortDirection.equalsIgnoreCase("asc")) {
            descending = false;
        } else if (sortDirection.equalsIgnoreCase("desc")) {
            descending = true;
        } else {
            throw new InvalidListQueryException("sortDirection must be asc or desc");
        }
        Comparator<T> requested = (left, right) -> compare(field.apply(left), field.apply(right), descending);
        rows.sort(requested.thenComparing(id));
    }

    private static int compare(Comparable<?> left, Comparable<?> right, boolean descending) {
        if (left == right) return 0;
        if (left == null) return 1;
        if (right == null) return -1;
        @SuppressWarnings("unchecked")
        int result = ((Comparable<Object>) left).compareTo(right);
        return descending ? -Integer.signum(result) : Integer.signum(result);
    }
}
