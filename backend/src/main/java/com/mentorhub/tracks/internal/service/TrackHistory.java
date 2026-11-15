package com.mentorhub.tracks.internal.service;

import java.util.List;

/**
 * A zero-based page of a track's history, newest first.
 *
 * @param size the requested page size
 * @param totalItems the number of entries on all pages, including entries left out of {@code items}
 */
public record TrackHistory(List<TrackHistoryItem> items, int page, int size, long totalItems) {

    public TrackHistory {
        items = List.copyOf(items);
    }
}
