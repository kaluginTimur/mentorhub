package com.mentorhub.users.internal.dev;

import com.mentorhub.users.Role;
import com.mentorhub.users.internal.domain.UserStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * The development users of the local Keycloak realm ({@code infra/keycloak/mentorhub-realm.json}).
 *
 * @param issuerUri the issuer of their identities
 * @param users the users to create or update; a realm user missing here stays unlinked (not invited)
 */
@ConfigurationProperties("mentorhub.development-users")
@Validated
record DevelopmentUsersProperties(
        @NotBlank String issuerUri, @DefaultValue @Valid List<DevelopmentUser> users) {

    /** @param subject the user's fixed id in the realm, which is the token's {@code sub} */
    record DevelopmentUser(
            @NotBlank String subject,
            @NotBlank String email,
            @Nullable String firstName,
            @Nullable String lastName,
            @NotNull UserStatus status,
            @DefaultValue List<Role> roles) {}
}
