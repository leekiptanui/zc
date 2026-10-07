package com.zimasahealth.zcare.common.api;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/**
 * A page of thin projections (04B section 8): 1-based, with the request's {@code page} and
 * {@code pageSize} reflected back. An empty result is a success with {@code items: []}.
 */
public record PagedResponse<T>(
        List<T> items,
        int count,
        int page,
        int pageSize,
        long totalItems,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious) {

    public static <E, T> PagedResponse<T> of(Page<E> source, PageParams params, Function<E, T> mapper) {
        List<T> items = source.getContent().stream().map(mapper).toList();
        int totalPages = Math.max(1, source.getTotalPages());
        return new PagedResponse<>(items, items.size(), params.page(), params.pageSize(),
                source.getTotalElements(), totalPages, params.page() < totalPages, params.page() > 1);
    }

    /** {@code ["proceed", "next_page"]} while more pages remain, else {@code ["proceed"]}. */
    public List<String> nextActions() {
        return hasNext ? List.of("proceed", "next_page") : List.of("proceed");
    }
}
