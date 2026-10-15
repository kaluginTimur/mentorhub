package com.mentorhub.users.internal.service;

import com.mentorhub.users.Permission;
import com.mentorhub.users.Role;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** An active user as {@code GET /api/v1/me} shows it; roles and permissions in enum order. */
public record UserProfile(
        UUID id,
        String email,
        @Nullable String firstName,
        @Nullable String lastName,
        List<Role> roles,
        List<Permission> permissions) {}
