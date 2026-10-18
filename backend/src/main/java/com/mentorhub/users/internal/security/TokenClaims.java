package com.mentorhub.users.internal.security;

import com.mentorhub.users.internal.bootstrap.SignInClaims;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

/** Reads the claims the application uses from a validated token; roles and groups in the token are never read. */
final class TokenClaims {

    private TokenClaims() {}

    /** @throws InvalidBearerTokenException when the token has no issuer or subject */
    static SignInClaims of(Jwt jwt) {
        String issuer = jwt.getClaimAsString(JwtClaimNames.ISS);
        String subject = jwt.getSubject();
        if (issuer == null || subject == null) {
            throw new InvalidBearerTokenException("The token has no issuer or subject");
        }
        return new SignInClaims(
                issuer,
                subject,
                jwt.getClaimAsString("email"),
                Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified")),
                jwt.getClaimAsString("given_name"),
                jwt.getClaimAsString("family_name"));
    }
}
