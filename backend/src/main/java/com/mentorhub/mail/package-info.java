/**
 * Outgoing email, an infrastructure module without domain knowledge (spec invitation-emails, Modules). Public API:
 * {@link MailDelivery}, {@link MailMessage} and {@link MailDeliveryException}. Email is enabled when Spring Boot
 * configures a mail sender ({@code spring.mail.host} is set); then {@code mentorhub.mail.from} is required. Depends on
 * no other module.
 */
@NullMarked
package com.mentorhub.mail;

import org.jspecify.annotations.NullMarked;
