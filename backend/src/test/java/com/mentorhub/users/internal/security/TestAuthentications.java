package com.mentorhub.users.internal.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

import com.mentorhub.users.Permission;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Request post-processors that authenticate a {@code @WebMvcTest} request as a caller in a given access status. */
public final class TestAuthentications {

    private TestAuthentications() {}

    public static RequestPostProcessor activeUser(UUID userId, Permission... permissions) {
        return authentication(MentorHubAuthentication.active(jwt(), userId, Set.of(permissions)));
    }

    public static RequestPostProcessor disabledUser(UUID userId) {
        return authentication(MentorHubAuthentication.disabled(jwt(), userId));
    }

    public static RequestPostProcessor notInvited() {
        return authentication(MentorHubAuthentication.notInvited(jwt(), "unlinked-subject"));
    }

    private static Jwt jwt() {
        return Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .issuer(TestIssuer.ISSUER)
                .subject("test-subject")
                .build();
    }
}
