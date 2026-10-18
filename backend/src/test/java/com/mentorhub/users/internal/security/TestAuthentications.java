package com.mentorhub.users.internal.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import com.mentorhub.users.Permission;
import com.mentorhub.users.internal.domain.AccessStatus;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Request post-processors that authenticate a {@code @WebMvcTest} request as a caller in a given access status. */
public final class TestAuthentications {

    private TestAuthentications() {}

    public static RequestPostProcessor activeUser(UUID userId, Permission... permissions) {
        return authentication(activeUserAuthentication(userId, permissions));
    }

    /** For tests that call services directly: put it into the {@code SecurityContextHolder}. */
    public static Authentication activeUserAuthentication(UUID userId, Permission... permissions) {
        return MentorHubAuthentication.active(jwt(), userId, Set.of(permissions));
    }

    public static RequestPostProcessor disabledUser(UUID userId) {
        return authentication(MentorHubAuthentication.disabled(jwt(), userId));
    }

    public static RequestPostProcessor notInvited() {
        return authentication(MentorHubAuthentication.notInvited(jwt(), "unlinked-subject"));
    }

    /**
     * For tests that call services directly: a caller in the given status whose token carries the subject, the email
     * claims and the names Ivan Sidorov. {@code userId} is the user resolved at authentication, ignored when not
     * invited.
     */
    public static Authentication tokenAuthentication(
            AccessStatus status, @Nullable UUID userId, String subject, @Nullable String email, boolean emailVerified) {
        Jwt.Builder builder = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .issuer(TestIssuer.ISSUER)
                .subject(subject)
                .claim("email_verified", emailVerified)
                .claim("given_name", "Ivan")
                .claim("family_name", "Sidorov");
        if (email != null) {
            builder.claim("email", email);
        }
        Jwt jwt = builder.build();
        return switch (status) {
            case ACTIVE -> MentorHubAuthentication.active(jwt, Objects.requireNonNull(userId), Set.of());
            case DISABLED -> MentorHubAuthentication.disabled(jwt, Objects.requireNonNull(userId));
            case NOT_INVITED -> MentorHubAuthentication.notInvited(jwt, subject);
        };
    }

    private static Jwt jwt() {
        return Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .issuer(TestIssuer.ISSUER)
                .subject("test-subject")
                .build();
    }
}
