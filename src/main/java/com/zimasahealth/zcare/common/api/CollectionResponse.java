package com.zimasahealth.zcare.common.api;

import java.util.List;

/** An unpaged collection, wrapped so that {@code data} is never a bare array (ENG-STD-SB-001 section 2.7). */
public record CollectionResponse<T>(List<T> items, int count) {

    public static <T> CollectionResponse<T> of(List<T> items) {
        return new CollectionResponse<>(List.copyOf(items), items.size());
    }
}
