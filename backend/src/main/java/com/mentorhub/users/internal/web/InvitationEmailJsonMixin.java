package com.mentorhub.users.internal.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mentorhub.api.model.InvitationEmail;
import org.springframework.boot.jackson.JacksonMixin;

/** An absent {@code finishedAt} (a queued email) is left out of the JSON; the other properties are required. */
@JacksonMixin(InvitationEmail.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
abstract class InvitationEmailJsonMixin {}
