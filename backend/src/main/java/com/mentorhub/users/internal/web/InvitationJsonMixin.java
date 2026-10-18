package com.mentorhub.users.internal.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mentorhub.api.model.Invitation;
import org.springframework.boot.jackson.JacksonMixin;

/**
 * The contract leaves the acceptance and revocation properties out of the JSON when they do not apply, instead of
 * sending {@code null}. All of the invitation's nullable properties are optional ones, so it holds for the whole class.
 */
@JacksonMixin(Invitation.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
abstract class InvitationJsonMixin {}
