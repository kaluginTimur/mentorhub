package com.mentorhub.mail.internal.delivery;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Not Spring Boot's {@code MailProperties}, which configures the mail server ({@code spring.mail.*}).
 *
 * @param from the sender address, for example {@code MentorHub <no-reply@example.org>}; required when email is enabled
 */
@ConfigurationProperties("mentorhub.mail")
record MailSenderProperties(@Nullable String from) {}
