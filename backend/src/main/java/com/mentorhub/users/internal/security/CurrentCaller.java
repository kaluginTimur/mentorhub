package com.mentorhub.users.internal.security;

import com.mentorhub.users.internal.domain.Caller;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** The caller in any access status, for {@code GET /api/v1/me}; other code uses {@code CurrentUserProvider}. */
@Component
public class CurrentCaller {

    public Caller caller() {
        Authentication authentication =
                SecurityContextHolder.getContextHolderStrategy().getContext().getAuthentication();
        if (authentication instanceof MentorHubAuthentication caller) {
            return new Caller(caller.status(), caller.userId());
        }
        throw new IllegalStateException("No authenticated MentorHub caller in the security context");
    }
}
