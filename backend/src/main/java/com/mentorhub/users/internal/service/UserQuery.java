package com.mentorhub.users.internal.service;

import com.mentorhub.users.internal.domain.RoleFilter;
import com.mentorhub.users.internal.domain.UserStatus;
import org.jspecify.annotations.Nullable;

/**
 * The users list request (AC-1, AC-2); {@code null} filters do not filter.
 *
 * @param text searched in the email, first and last name after trimming; blank means no search
 */
public record UserQuery(
        @Nullable String text,
        @Nullable UserStatus status,
        @Nullable RoleFilter role,
        int page,
        int size) {}
