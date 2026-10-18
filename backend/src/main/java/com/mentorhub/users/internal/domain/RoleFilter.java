package com.mentorhub.users.internal.domain;

import com.mentorhub.users.Role;

/** Filters users by role: users with the role, or users without any role the code knows (ADR 0004). */
public sealed interface RoleFilter {

    record WithRole(Role role) implements RoleFilter {}

    record WithoutRoles() implements RoleFilter {}
}
