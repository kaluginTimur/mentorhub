package com.mentorhub.users.internal.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.mentorhub.users.Permission;
import com.mentorhub.users.Role;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StoredRolesTests {

    @Test
    @DisplayName("PERM-2.1 Permissions of several roles are combined")
    void permissionsOfSeveralRolesAreCombined() {
        assertThat(new StoredRoles().permissions(List.of(Role.MENTOR.name(), Role.ADMIN.name())))
                .containsExactlyInAnyOrder(Permission.USERS_READ, Permission.USERS_MANAGE, Permission.USERS_INVITE);
    }
}
