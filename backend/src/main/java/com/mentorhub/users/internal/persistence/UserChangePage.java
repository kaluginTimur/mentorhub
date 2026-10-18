package com.mentorhub.users.internal.persistence;

import java.util.List;

/**
 * A page of a user's audit entries, newest first. Entries the code cannot read are left out of {@code items} but
 * counted in {@code totalItems}, so a page can hold fewer items than its size.
 */
public record UserChangePage(List<RecordedUserChange> items, long totalItems) {

    public UserChangePage {
        items = List.copyOf(items);
    }
}
