package com.mentorhub.users.internal.security;

import com.mentorhub.users.internal.bootstrap.AdministratorBootstrap;
import com.mentorhub.users.internal.bootstrap.SignInClaims;
import com.mentorhub.users.internal.domain.StoredRoles;
import com.mentorhub.users.internal.persistence.UserRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
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
        String issuer = jwt.getClaimAsString(JwtClaimNames.ISS);
        String subject = jwt.getSubject();
        if (issuer == null || subject == null) {
            throw new InvalidBearerTokenException("The token has no issuer or subject");
        }
        bootstrap.applyIfEligible(new SignInClaims(
                issuer,
                subject,
                jwt.getClaimAsString("email"),
                Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified")),
                jwt.getClaimAsString("given_name"),
                jwt.getClaimAsString("family_name")));
        return users.findAccess(issuer, subject)
                .map(access -> switch (access.status()) {
                    case ACTIVE ->
                        MentorHubAuthentication.active(
                                jwt, access.userId(), storedRoles.permissions(access.roleNames()));
                    case DISABLED -> MentorHubAuthentication.disabled(jwt, access.userId());
                })
                .orElseGet(() -> MentorHubAuthentication.notInvited(jwt, subject));
    }
}
