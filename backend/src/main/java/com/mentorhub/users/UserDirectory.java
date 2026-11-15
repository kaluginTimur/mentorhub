package com.mentorhub.users;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Reads of users for other modules, so they never read the {@code users} tables. Lists are sorted by name ignoring
 * case, then by email ignoring case, then by id.
 */
public interface UserDirectory {

    /** The users with the given ids, in any status; ids no user has are left out. */
    List<DirectoryUser> findUsers(Collection<UUID> userIds);

    /** The active users who hold the role. */
    List<DirectoryUser> findActiveUsersWithRole(Role role);

    /** Whether the id is an active user who holds the role; {@code false} for an id no user has. */
    boolean isActiveWithRole(UUID userId, Role role);
}
