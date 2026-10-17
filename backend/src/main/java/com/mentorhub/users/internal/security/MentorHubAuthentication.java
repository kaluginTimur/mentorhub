package com.mentorhub.users.internal.security;

import com.mentorhub.users.CurrentUser;
import com.mentorhub.users.Permission;
import com.mentorhub.users.internal.domain.AccessStatus;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * The caller of a request with a validated token: the access status and, for a linked user, {@code users.id}. Only an
 * active user has permissions, granted as authorities named after the {@link Permission} constants.
 */
final class MentorHubAuthentication extends JwtAuthenticationToken {

    private final AccessStatus status;
    private final @Nullable UUID userId;
    private final Set<Permission> permissions;

    private MentorHubAuthentication(
            Jwt jwt, AccessStatus status, @Nullable UUID userId, Set<Permission> permissions, String name) {
        super(jwt, authorities(permissions), name);
        this.status = status;
        this.userId = userId;
        this.permissions = Set.copyOf(permissions);
    }

    static MentorHubAuthentication active(Jwt jwt, UUID userId, Set<Permission> permissions) {
        return new MentorHubAuthentication(jwt, AccessStatus.ACTIVE, userId, permissions, userId.toString());
    }

    static MentorHubAuthentication disabled(Jwt jwt, UUID userId) {
        return new MentorHubAuthentication(jwt, AccessStatus.DISABLED, userId, Set.of(), userId.toString());
    }

    static MentorHubAuthentication notInvited(Jwt jwt, String subject) {
        return new MentorHubAuthentication(jwt, AccessStatus.NOT_INVITED, null, Set.of(), subject);
    }

    AccessStatus status() {
        return status;
    }

    @Nullable
    UUID userId() {
        return userId;
    }

    Optional<CurrentUser> activeUser() {
        if (status != AccessStatus.ACTIVE || userId == null) {
            return Optional.empty();
        }
        return Optional.of(new CurrentUser(userId, permissions));
    }

    private static Set<GrantedAuthority> authorities(Set<Permission> permissions) {
        return Set.copyOf(permissions.stream()
                .map(permission -> new SimpleGrantedAuthority(permission.name()))
                .toList());
    }
}
