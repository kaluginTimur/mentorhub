package com.mentorhub.users.internal.security;

import com.mentorhub.users.internal.bootstrap.AdministratorBootstrap;
import com.mentorhub.users.internal.bootstrap.SignInClaims;
import com.mentorhub.users.internal.domain.StoredRoles;
import com.mentorhub.users.internal.persistence.UserRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Turns a validated token into the caller on every request: {@code (iss, sub)} to {@code users.id} through
 * {@code user_identities}, after the administrator bootstrap. Roles and groups in the token are never read.
 */
@Component
class UserAuthenticationResolver {

    private final UserRepository users;
    private final StoredRoles storedRoles;
    private final AdministratorBootstrap bootstrap;

    UserAuthenticationResolver(UserRepository users, StoredRoles storedRoles, AdministratorBootstrap bootstrap) {
        this.users = users;
        this.storedRoles = storedRoles;
        this.bootstrap = bootstrap;
    }

    MentorHubAuthentication resolve(Jwt jwt) {
        SignInClaims claims = TokenClaims.of(jwt);
        bootstrap.applyIfEligible(claims);
        String subject = claims.subject();
        return users.findAccess(claims.issuer(), subject)
                .map(access -> switch (access.status()) {
                    case ACTIVE ->
                        MentorHubAuthentication.active(
                                jwt, access.userId(), storedRoles.permissions(access.roleNames()));
                    case DISABLED -> MentorHubAuthentication.disabled(jwt, access.userId());
                })
                .orElseGet(() -> MentorHubAuthentication.notInvited(jwt, subject));
    }
}
