package com.mentorhub.users;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Reads of users for other modules, so they never read the {@code users} tables. Lists and pages are sorted by name
 * ignoring case, then by email ignoring case, then by id.
 */
public interface UserDirectory {

    /** The users with the given ids, in any status; ids no user has are left out. */
    List<DirectoryUser> findUsers(Collection<UUID> userIds);

    /**
     * A page of the users with the given ids, in any status; ids no user has are left out.
     *
     * @param page zero-based
     */
    DirectoryPage findUsers(Collection<UUID> userIds, int page, int size);

    /** The active users who hold the role. */
    List<DirectoryUser> findActiveUsersWithRole(Role role);

    /**
     * A page of the active users who hold the role, except the excluded ids.
     *
     * @param text a trimmed, non-blank substring of the email, first or last name, matched literally and ignoring case;
     *     {@code null} for no search
     * @param excludedUserIds bounded by the number of users of the center
     * @param page zero-based
     */
    DirectoryPage findActiveUsersWithRole(
            Role role, @Nullable String text, Collection<UUID> excludedUserIds, int page, int size);

    /** Whether the id is an active user who holds the role; {@code false} for an id no user has. */
    boolean isActiveWithRole(UUID userId, Role role);

    /** The contacts of the users with the given ids who are active and hold the role. */
    List<UserContact> findActiveContactsWithRole(Collection<UUID> userIds, Role role);
}
