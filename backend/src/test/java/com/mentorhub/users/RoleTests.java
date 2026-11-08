package com.mentorhub.users;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RoleTests {

    @Test
    @DisplayName("PERM-1.1 An administrator holds the user permissions")
    void adminHasUserPermissions() {
        assertThat(Role.ADMIN.permissions())
                .containsExactlyInAnyOrder(Permission.USERS_READ, Permission.USERS_MANAGE, Permission.USERS_INVITE);
    }

    @Test
    @DisplayName("PERM-1.1 The other roles hold no permission")
    void otherRolesHaveNoPermissions() {
        assertThat(Arrays.stream(Role.values()).filter(role -> role != Role.ADMIN))
                .allSatisfy(role -> assertThat(role.permissions()).isEmpty());
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
