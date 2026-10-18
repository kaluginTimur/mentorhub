package com.mentorhub.users.internal.dev;

import com.mentorhub.users.Role;
import com.mentorhub.users.internal.domain.DisableReason;
import com.mentorhub.users.internal.domain.UserStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
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

    /**
     * @param subject the user's fixed id in the realm, which is the token's {@code sub}
     * @param disabledReason required for a disabled user, absent for an active one
     * @param disabledComment optional for a disabled user, required with {@code OTHER}; absent for an active one
     */
    record DevelopmentUser(
            @NotBlank String subject,
            @NotBlank String email,
            @Nullable String firstName,
            @Nullable String lastName,
            @NotNull UserStatus status,
            @Nullable DisableReason disabledReason,
            @Nullable String disabledComment,
            @DefaultValue List<Role> roles) {

        @AssertTrue(
                message = "a disabled user needs disabled-reason (and disabled-comment with OTHER); an active user"
                        + " has neither")
        public boolean isDisabledReasonConsistent() {
            boolean hasComment = disabledComment != null && !disabledComment.isBlank();
            if (status != UserStatus.DISABLED) {
                return disabledReason == null && disabledComment == null;
            }
            return disabledReason != null
                    && (disabledComment == null || hasComment)
                    && (disabledReason != DisableReason.OTHER || hasComment);
        }
    }
}
