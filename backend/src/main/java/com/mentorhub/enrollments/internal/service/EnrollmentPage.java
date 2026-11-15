package com.mentorhub.enrollments.internal.service;

import java.util.List;

/**
 * A zero-based page of results.
 *
 * @param size the requested page size
 * @param totalItems the number of matching items on all pages
 */
public record EnrollmentPage<T>(List<T> items, int page, int size, long totalItems) {

    public EnrollmentPage {
        items = List.copyOf(items);
    }
}
