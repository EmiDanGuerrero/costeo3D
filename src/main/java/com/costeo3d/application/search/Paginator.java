package com.costeo3d.application.search;

import java.util.List;

public final class Paginator {

    private Paginator() {
    }

    public static <T> List<T> paginate(
            List<T> items,
            int pageNumber,
            int pageSize) {

        if (items == null) {
            throw new IllegalArgumentException(
                    "Items cannot be null.");
        }

        if (pageNumber <= 0) {
            throw new IllegalArgumentException(
                    "Page number must be greater than zero.");
        }

        if (pageSize <= 0) {
            throw new IllegalArgumentException(
                    "Page size must be greater than zero.");
        }

        int fromIndex = (pageNumber - 1) * pageSize;

        if (fromIndex >= items.size()) {
            return List.of();
        }

        int toIndex = Math.min(
                fromIndex + pageSize,
                items.size());

        return List.copyOf(
                items.subList(fromIndex, toIndex));
    }
}