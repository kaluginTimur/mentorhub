package com.mentorhub.users.internal.security;

import com.mentorhub.users.CurrentUser;
import com.mentorhub.users.CurrentUserProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
class SecurityContextCurrentUserProvider implements CurrentUserProvider {

    @Override
    public CurrentUser currentUser() {
        Authentication authentication =
                SecurityContextHolder.getContextHolderStrategy().getContext().getAuthentication();
        if (authentication instanceof MentorHubAuthentication caller) {
            return caller.activeUser().orElseThrow(() -> new IllegalStateException("The caller is not an active user"));
        }
        throw new IllegalStateException("No authenticated MentorHub user in the security context");
    }
}
