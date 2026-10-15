package com.mentorhub.users.internal.security;

import com.mentorhub.users.CurrentUser;
import com.mentorhub.users.CurrentUserProvider;
import com.mentorhub.users.NoPermissionRequired;
import com.mentorhub.users.Permission;
import com.mentorhub.users.RequirePermission;
import com.mentorhub.web.NotFoundException;
import java.util.Map;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Test-only endpoints that exercise the gate, permissions and error handling. */
@RestController
@RequestMapping("/api/v1/test")
class PermissionProbeController {

    private final CurrentUserProvider currentUsers;

    PermissionProbeController(CurrentUserProvider currentUsers) {
        this.currentUsers = currentUsers;
    }

    @GetMapping("/current-user")
    @NoPermissionRequired
    public Map<String, Object> currentUser() {
        CurrentUser user = currentUsers.currentUser();
        return Map.of("id", user.id(), "permissions", user.permissions());
    }

    @GetMapping("/users-read")
    @RequirePermission(Permission.USERS_READ)
    public Map<String, String> usersRead() {
        return Map.of("result", "granted");
    }

    @GetMapping("/missing")
    @NoPermissionRequired
    public void missing() {
        throw new NotFoundException();
    }

    @GetMapping("/denied")
    @NoPermissionRequired
    public void denied() {
        throw new AccessDeniedException("Refused by a resource-level check");
    }

    @GetMapping("/failure")
    @NoPermissionRequired
    public void failure() {
        throw new IllegalStateException("Unexpected failure");
    }
}
