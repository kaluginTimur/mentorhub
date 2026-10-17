package com.mentorhub.users;

/** Gives services the active user of the current request. */
public interface CurrentUserProvider {

    /**
     * @throws IllegalStateException when the request has no active user; the gate admits only active users to every
     *     operation except {@code GET /api/v1/me}, so this indicates a programming error
     */
    CurrentUser currentUser();
}
