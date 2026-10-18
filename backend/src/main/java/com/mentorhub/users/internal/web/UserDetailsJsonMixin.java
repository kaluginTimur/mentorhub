package com.mentorhub.users.internal.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mentorhub.api.model.DisableReason;
import com.mentorhub.api.model.UserDetails;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.jackson.JacksonMixin;

/**
 * Leaves the disable reason and comment out of the JSON unless they are set. Per property, not per class: the required
 * nullable {@code firstName}, {@code lastName} and {@code phone} must still be sent as {@code null}.
 */
@JacksonMixin(UserDetails.class)
abstract class UserDetailsJsonMixin {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    abstract @Nullable DisableReason getDisabledReason();

    @JsonInclude(JsonInclude.Include.NON_NULL)
    abstract @Nullable String getDisabledComment();
}
