package com.mentorhub.mail.internal.delivery;

import com.mentorhub.mail.MailDelivery;
import com.mentorhub.mail.MailDeliveryException;
import com.mentorhub.mail.MailMessage;
import jakarta.mail.internet.InternetAddress;
import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

/**
 * Whether email is enabled is read from Boot's mail sender when this bean is created, not with
 * {@code @ConditionalOnMissingBean}: that condition would be evaluated before Boot's mail auto-configuration and never
 * see the sender.
 */
class JavaMailDelivery implements MailDelivery {

    private final @Nullable JavaMailSender sender;
    private final @Nullable InternetAddress from;

    JavaMailDelivery(ObjectProvider<JavaMailSender> senders, MailSenderProperties properties) {
        this.sender = senders.getIfAvailable();
        this.from = sender == null ? null : SenderAddress.parse(properties.from());
    }

    @Override
    public boolean isEnabled() {
        return sender != null;
    }

    @Override
    public void send(MailMessage message) {
        JavaMailSender mailSender = sender;
        InternetAddress fromAddress = from;
        if (mailSender == null || fromAddress == null) {
            throw new IllegalStateException("Email is not enabled: spring.mail.host is not set");
        }
        try {
            mailSender.send(mime -> {
                MimeMessageHelper helper = new MimeMessageHelper(mime, StandardCharsets.UTF_8.name());
                helper.setFrom(fromAddress);
                helper.setTo(new InternetAddress(message.to(), true));
                helper.setSubject(message.subject());
                helper.setText(message.text(), false);
            });
        } catch (MailException e) {
            throw new MailDeliveryException(describe(e), e);
        }
    }

    /** The exception's own message and its most specific cause, on one line. */
    static String describe(MailException e) {
        StringBuilder description = new StringBuilder(e.getClass().getSimpleName());
        if (e.getMessage() != null) {
            description.append(": ").append(e.getMessage());
        }
        Throwable cause = NestedExceptionUtils.getMostSpecificCause(e);
        if (cause != e) {
            description.append("; caused by ").append(cause);
        }
        return description.toString().replaceAll("\\s+", " ").strip();
    }
}
