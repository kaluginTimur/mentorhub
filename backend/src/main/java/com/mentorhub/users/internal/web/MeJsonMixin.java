package com.mentorhub.users.internal.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mentorhub.api.model.Me;
import org.springframework.boot.jackson.JacksonMixin;

/**
 * The contract leaves {@code Me.user} out of the JSON for callers that are not active, instead of sending
 * {@code null}. Applies to {@code Me} only: required nullable properties such as {@code MeUser.firstName} must still be
 * sent as {@code null}.
 */
@JacksonMixin(Me.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
abstract class MeJsonMixin {}
