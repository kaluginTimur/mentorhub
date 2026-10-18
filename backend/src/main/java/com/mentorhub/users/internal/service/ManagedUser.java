package com.mentorhub.users.internal.service;

import com.mentorhub.users.Role;
import com.mentorhub.users.internal.domain.DisableReason;
import com.mentorhub.users.internal.domain.UserStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A user as administrators see it (AC-4). The reason is present only when disabled, the comment only when disabled
 * and set. Roles are the known ones, in enum order.
 */
public record ManagedUser(
        UUID id,
        String email,
        @Nullable String firstName,
        @Nullable String lastName,
        @Nullable String phone,
        UserStatus status,
        @Nullable DisableReason disabledReason,
        @Nullable String disabledComment,
        List<Role> roles,
        Instant createdAt) {

    public ManagedUser {
        roles = List.copyOf(roles);
    }
}
