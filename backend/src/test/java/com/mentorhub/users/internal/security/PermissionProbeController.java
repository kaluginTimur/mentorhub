package com.mentorhub.users.internal.security;

import com.mentorhub.users.CurrentUser;
import com.mentorhub.users.CurrentUserProvider;
import com.mentorhub.users.NoPermissionRequired;
import com.mentorhub.users.Permission;
import com.mentorhub.users.RequirePermission;
import com.mentorhub.web.NotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Test-only endpoints that exercise the gate, permissions and error handling. */
@RestController
@RequestMapping("/api/v1/test")
class PermissionProbeController {

    private final CurrentUserProvider currentUsers;
    private final ProbeResources resources;

    PermissionProbeController(CurrentUserProvider currentUsers, ProbeResources resources) {
        this.currentUsers = currentUsers;
        this.resources = resources;
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

    @PostMapping("/managed-resources/{id}")
    @RequirePermission(Permission.USERS_MANAGE)
    public void change(@PathVariable UUID id) {
        resources.change(id);
    }

    @PostMapping("/managed-resources")
    @RequirePermission(Permission.USERS_MANAGE)
    public void create(@Valid @RequestBody ProbeBody body) {
        resources.create(body);
    }

    /** A request body with validated properties, as the generated request types have. */
    record ProbeBody(
            @NotBlank @Nullable String name,
            @NotNull @Min(1) @Nullable Integer count) {}

    @GetMapping("/missing")
    @NoPermissionRequired
    public void missing() {
        throw new NotFoundException();
    }

    /** The resource "restricted" exists but the caller may not access it; the service answers as for a missing one. */
    @GetMapping("/resources/{name}")
    @NoPermissionRequired
    public void resource(@PathVariable String name) {
        throw new NotFoundException();
    }

    @GetMapping("/failure")
    @NoPermissionRequired
    public void failure() {
        throw new IllegalStateException("Unexpected failure");
    }
}
