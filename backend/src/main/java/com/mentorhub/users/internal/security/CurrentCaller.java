package com.mentorhub.users.internal.security;

import com.mentorhub.users.internal.bootstrap.SignInClaims;
import com.mentorhub.users.internal.domain.Caller;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * The caller in any access status, for the operations the gate admits for every signed-in caller
 * ({@code GET /api/v1/me}, accepting an invitation); other code uses {@code CurrentUserProvider}.
 */
@Component
public class CurrentCaller {

    public Caller caller() {
        Authentication authentication =
                SecurityContextHolder.getContextHolderStrategy().getContext().getAuthentication();
        if (authentication instanceof MentorHubAuthentication caller) {
            SignInClaims claims = TokenClaims.of(caller.getToken());
            return new Caller(
                    caller.status(),
                    caller.userId(),
                    claims.issuer(),
                    claims.subject(),
                    claims.email(),
                    claims.emailVerified(),
                    claims.givenName(),
                    claims.familyName());
        }
        throw new IllegalStateException("No authenticated MentorHub caller in the security context");
    }
}
