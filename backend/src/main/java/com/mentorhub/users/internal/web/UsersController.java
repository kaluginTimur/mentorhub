package com.mentorhub.users.internal.web;

import com.mentorhub.api.UsersApi;
import com.mentorhub.api.model.DisableRequest;
import com.mentorhub.api.model.HistoryPage;
import com.mentorhub.api.model.Me;
import com.mentorhub.api.model.ProfileUpdate;
import com.mentorhub.api.model.Role;
import com.mentorhub.api.model.RoleFilter;
import com.mentorhub.api.model.RolesUpdate;
import com.mentorhub.api.model.UserDetails;
import com.mentorhub.api.model.UserPage;
import com.mentorhub.api.model.UserStatus;
import com.mentorhub.users.NoPermissionRequired;
import com.mentorhub.users.Permission;
import com.mentorhub.users.RequirePermission;
import com.mentorhub.users.internal.service.MeService;
import com.mentorhub.users.internal.service.ProfileInput;
import com.mentorhub.users.internal.service.ProfileService;
import com.mentorhub.users.internal.service.UserAdministrationService;
import com.mentorhub.users.internal.service.UserQuery;
import com.mentorhub.web.ValidationException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.RestController;

/**
 * The {@code Users} tag: the caller's own user and profile, and user administration. Parameter names equal the
 * request parameter names, because method validation reports violations by Java parameter name.
 */
@RestController
class UsersController implements UsersApi {

    private final MeService meService;
    private final ProfileService profileService;
    private final UserAdministrationService administration;

    UsersController(MeService meService, ProfileService profileService, UserAdministrationService administration) {
        this.meService = meService;
        this.profileService = profileService;
        this.administration = administration;
    }

    @Override
    @NoPermissionRequired
    public Me getMe() {
        return UsersMapper.me(meService.me());
    }

    @Override
    @NoPermissionRequired
    public Me updateMyProfile(ProfileUpdate profileUpdate) {
        profileService.updateOwnProfile(profileInput(profileUpdate));
        return UsersMapper.me(meService.me());
    }

    @Override
    @RequirePermission(Permission.USERS_READ)
    public UserPage listUsers(
            @Nullable String q, @Nullable UserStatus status, @Nullable RoleFilter role, Integer page, Integer size) {
        return UsersMapper.userPage(administration.list(
                new UserQuery(q, UsersMapper.status(status), UsersMapper.roleFilter(role), page, size)));
    }

    @Override
    @RequirePermission(Permission.USERS_READ)
    public UserDetails getUser(UUID id) {
        return UsersMapper.userDetails(administration.get(id));
    }

    @Override
    @RequirePermission(Permission.USERS_READ)
    public HistoryPage getUserHistory(UUID id, Integer page, Integer size) {
        return UsersMapper.historyPage(administration.history(id, page, size));
    }

    @Override
    @RequirePermission(Permission.USERS_MANAGE)
    public UserDetails disableUser(UUID id, DisableRequest disableRequest) {
        return UsersMapper.userDetails(administration.disable(
                id, UsersMapper.disableReason(disableRequest.getReason()), disableRequest.getComment()));
    }

    @Override
    @RequirePermission(Permission.USERS_MANAGE)
    public UserDetails enableUser(UUID id) {
        return UsersMapper.userDetails(administration.enable(id));
    }

    @Override
    @RequirePermission(Permission.USERS_MANAGE)
    public UserDetails setUserRoles(UUID id, RolesUpdate rolesUpdate) {
        Set<Role> roles = rolesUpdate.getRoles();
        // Bean validation checks the set, not its items: `[null]` binds to a set containing null.
        if (roles.stream().anyMatch(Objects::isNull)) {
            throw ValidationException.of("roles", "must not contain null");
        }
        return UsersMapper.userDetails(administration.setRoles(
                id, roles.stream().map(UsersMapper::role).collect(Collectors.toUnmodifiableSet())));
    }

    @Override
    @RequirePermission(Permission.USERS_MANAGE)
    public UserDetails updateUserProfile(UUID id, ProfileUpdate profileUpdate) {
        return UsersMapper.userDetails(administration.updateProfile(id, profileInput(profileUpdate)));
    }

    private static ProfileInput profileInput(ProfileUpdate update) {
        return new ProfileInput(update.getFirstName(), update.getLastName(), update.getPhone());
    }
}
