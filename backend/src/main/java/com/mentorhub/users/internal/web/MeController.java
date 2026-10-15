package com.mentorhub.users.internal.web;

import com.mentorhub.api.UsersApi;
import com.mentorhub.api.model.AccessStatus;
import com.mentorhub.api.model.Me;
import com.mentorhub.api.model.MeUser;
import com.mentorhub.api.model.Permission;
import com.mentorhub.api.model.Role;
import com.mentorhub.users.NoPermissionRequired;
import com.mentorhub.users.internal.service.MeDetails;
import com.mentorhub.users.internal.service.MeService;
import com.mentorhub.users.internal.service.UserProfile;
import org.springframework.web.bind.annotation.RestController;

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
                    user.roles().stream().map(role -> Role.valueOf(role.name())).toList(),
                    user.permissions().stream()
                            .map(permission -> Permission.valueOf(permission.name()))
                            .toList()));
        }
        return me;
    }
}
