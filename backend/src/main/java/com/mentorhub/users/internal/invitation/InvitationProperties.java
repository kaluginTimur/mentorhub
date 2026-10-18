package com.mentorhub.users.internal.invitation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * @param appUrl the frontend's public URL, used to build links ({@code <app-url>/invite/<token>}). Required: startup
 *     fails without it
 */
@ConfigurationProperties("mentorhub")
@Validated
record InvitationProperties(
        @NotNull URI appUrl, @DefaultValue @Valid Invitations invitations) {

    /** @param linkLifetime how long a link is valid after it is created or issued again */
    record Invitations(@DefaultValue("7d") Duration linkLifetime) {

        Invitations {
            if (linkLifetime.isNegative() || linkLifetime.isZero()) {
                throw new IllegalArgumentException("mentorhub.invitations.link-lifetime must be positive");
            }
        }
    }

    Duration linkLifetime() {
        return invitations.linkLifetime();
    }

    URI link(String token) {
        String base = appUrl.toString();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return URI.create(base + "/invite/" + token);
    }
}
