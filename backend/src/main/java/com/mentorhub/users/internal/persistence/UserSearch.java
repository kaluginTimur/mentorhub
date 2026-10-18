package com.mentorhub.users.internal.persistence;

import com.mentorhub.users.internal.domain.RoleFilter;
import com.mentorhub.users.internal.domain.UserStatus;
import org.jspecify.annotations.Nullable;

/**
 * Filters of the users list, combined with AND; {@code null} means no filter.
 *
 * @param text a trimmed, non-blank substring of the email, first or last name, matched literally and ignoring case
 */
public record UserSearch(
        @Nullable String text,
        @Nullable UserStatus status,
        @Nullable RoleFilter role) {}
