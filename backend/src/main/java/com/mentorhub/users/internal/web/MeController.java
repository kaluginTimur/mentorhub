package com.mentorhub.users.internal.web;

import com.mentorhub.api.UsersApi;
import com.mentorhub.api.model.AccessStatus;
import com.mentorhub.api.model.DisableRequest;
import com.mentorhub.api.model.HistoryPage;
import com.mentorhub.api.model.Me;
import com.mentorhub.api.model.MeUser;
import com.mentorhub.api.model.Permission;
import com.mentorhub.api.model.ProfileUpdate;
import com.mentorhub.api.model.Role;
import com.mentorhub.api.model.RoleFilter;
import com.mentorhub.api.model.RolesUpdate;
import com.mentorhub.api.model.UserDetails;
import com.mentorhub.api.model.UserPage;
import com.mentorhub.api.model.UserStatus;
import com.mentorhub.users.NoPermissionRequired;
import com.mentorhub.users.RequirePermission;
import com.mentorhub.users.internal.service.MeDetails;
import com.mentorhub.users.internal.service.MeService;
import com.mentorhub.users.internal.service.UserProfile;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
class MeController implements UsersApi {

    private final MeService meService;

    MeController(MeService meService) {
        this.meService = meService;
    }

    @Override
    @NoPermissionRequired
    public Me getMe() {
        MeDetails details = meService.me();
        Me me = new Me(AccessStatus.valueOf(details.status().name()));
        UserProfile user = details.user();
        if (user != null) {
            me.setUser(new MeUser(
                    user.id(),
                    user.email(),
                    user.firstName(),
                    user.lastName(),
                    null, // TODO(user-administration step 4): phone
                    user.roles().stream().map(role -> Role.valueOf(role.name())).toList(),
                    user.permissions().stream()
                            .map(permission -> Permission.valueOf(permission.name()))
                            .toList()));
        }
        return me;
    }

    // TODO(user-administration step 4): temporary stubs until the real implementation replaces them.

    @Override
    @NoPermissionRequired
    public Me updateMyProfile(ProfileUpdate profileUpdate) {
        throw notImplemented();
    }

    @Override
    @RequirePermission(com.mentorhub.users.Permission.USERS_READ)
    public UserPage listUsers(
            @Nullable String q, @Nullable UserStatus status, @Nullable RoleFilter role, Integer page, Integer size) {
        throw notImplemented();
    }

    @Override
    @RequirePermission(com.mentorhub.users.Permission.USERS_READ)
    public UserDetails getUser(UUID id) {
        throw notImplemented();
    }

    @Override
    @RequirePermission(com.mentorhub.users.Permission.USERS_READ)
    public HistoryPage getUserHistory(UUID id, Integer page, Integer size) {
        throw notImplemented();
    }

    @Override
    @RequirePermission(com.mentorhub.users.Permission.USERS_MANAGE)
    public UserDetails disableUser(UUID id, DisableRequest disableRequest) {
        throw notImplemented();
    }

    @Override
    @RequirePermission(com.mentorhub.users.Permission.USERS_MANAGE)
    public UserDetails enableUser(UUID id) {
        throw notImplemented();
    }

    @Override
    @RequirePermission(com.mentorhub.users.Permission.USERS_MANAGE)
    public UserDetails setUserRoles(UUID id, RolesUpdate rolesUpdate) {
        throw notImplemented();
    }

    @Override
    @RequirePermission(com.mentorhub.users.Permission.USERS_MANAGE)
    public UserDetails updateUserProfile(UUID id, ProfileUpdate profileUpdate) {
        throw notImplemented();
    }

    private static ResponseStatusException notImplemented() {
        return new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
    }
}
