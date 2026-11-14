package com.mentorhub.users;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RoleTests {

    // The role table of PERM-1: every role the version knows, with exactly its permissions.
    private static final Map<String, Set<Permission>> TABLE = Map.of(
            "ADMIN", EnumSet.of(Permission.USERS_READ, Permission.USERS_MANAGE, Permission.USERS_INVITE),
            "COORDINATOR", EnumSet.noneOf(Permission.class),
            "MENTOR", EnumSet.noneOf(Permission.class),
            "STUDENT", EnumSet.noneOf(Permission.class));

    @Test
    @DisplayName("PERM-1.1 A role holds exactly the permissions in the table")
    void everyRoleHoldsExactlyThePermissionsOfTheTable() {
        assertThat(Arrays.stream(Role.values()).map(Enum::name)).containsExactlyInAnyOrderElementsOf(TABLE.keySet());
        assertThat(Role.values())
                .allSatisfy(
                        role -> assertThat(role.permissions()).as(role.name()).isEqualTo(TABLE.get(role.name())));
    }

    @Test
    void contractRolesEqualCodeRoles() {
        assertThat(Arrays.stream(com.mentorhub.api.model.Role.values()).map(Enum::name))
                .containsExactlyElementsOf(
                        Arrays.stream(Role.values()).map(Enum::name).toList());
    }

    @Test
    void contractPermissionsEqualCodePermissions() {
        assertThat(Arrays.stream(com.mentorhub.api.model.Permission.values()).map(Enum::name))
                .containsExactlyElementsOf(
                        Arrays.stream(Permission.values()).map(Enum::name).toList());
    }
}
