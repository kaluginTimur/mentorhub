package com.mentorhub.users;

import java.util.EnumSet;
import java.util.Set;

/**
 * A role and the permissions it grants (ADR 0004). Must equal the {@code roles} table and the {@code Role} enum of
 * the API contract. Authorization checks use permissions, never roles.
 */
public enum Role {
    ADMIN(EnumSet.of(Permission.USERS_READ, Permission.USERS_MANAGE)),
    COORDINATOR(EnumSet.noneOf(Permission.class)),
    MENTOR(EnumSet.noneOf(Permission.class)),
    STUDENT(EnumSet.noneOf(Permission.class)),
    OBSERVER(EnumSet.noneOf(Permission.class));

    private final Set<Permission> permissions;

    Role(EnumSet<Permission> permissions) {
        this.permissions = Set.copyOf(permissions);
    }

    public Set<Permission> permissions() {
        return permissions;
    }
}
