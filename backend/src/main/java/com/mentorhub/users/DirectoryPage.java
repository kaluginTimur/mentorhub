package com.mentorhub.users;

import java.util.List;

/**
 * One zero-based page of users and the number of matching users on all pages.
 *
 * @param items sorted as {@link UserDirectory} says
 */
public record DirectoryPage(List<DirectoryUser> items, long totalItems) {

    public DirectoryPage {
        items = List.copyOf(items);
    }
}
