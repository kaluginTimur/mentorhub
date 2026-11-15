package com.mentorhub.tracks.internal.persistence;

import java.util.List;

/**
 * A page of a track's audit entries, newest first. Entries the code cannot read are left out of {@code items} but
 * counted in {@code totalItems}, so a page can hold fewer items than its size.
 */
public record TrackChangePage(List<RecordedTrackChange> items, long totalItems) {

    public TrackChangePage {
        items = List.copyOf(items);
    }
}
