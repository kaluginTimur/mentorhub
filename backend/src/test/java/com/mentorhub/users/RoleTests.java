package com.mentorhub.users;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class RoleTests {

    @Test
    void onlyAdminHasUserPermissions() {
        assertThat(Role.ADMIN.permissions()).containsExactlyInAnyOrder(Permission.USERS_READ, Permission.USERS_MANAGE);
        assertThat(Arrays.stream(Role.values()).filter(role -> role != Role.ADMIN))
                .allSatisfy(role -> assertThat(role.permissions()).isEmpty());
    }

    // AC-21
    @Test
    void contractRolesEqualCodeRoles() {
        assertThat(Arrays.stream(com.mentorhub.api.model.Role.values()).map(Enum::name))
                .containsExactlyElementsOf(
                        Arrays.stream(Role.values()).map(Enum::name).toList());
    }

    // AC-21
    @Test
    void contractPermissionsEqualCodePermissions() {
        assertThat(Arrays.stream(com.mentorhub.api.model.Permission.values()).map(Enum::name))
                .containsExactlyElementsOf(
                        Arrays.stream(Permission.values()).map(Enum::name).toList());
    }
}
