package com.mentorhub.users.internal.service;

import java.util.List;

/**
 * A zero-based page of results.
 *
 * @param size the requested page size
 * @param totalItems the number of matching items on all pages
 */
public record ResultPage<T>(List<T> items, int page, int size, long totalItems) {

    public ResultPage {
        items = List.copyOf(items);
    }
}
