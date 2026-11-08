package com.mentorhub.mail.internal.delivery;

import static com.mentorhub.mail.internal.delivery.JavaMailDeliveryTests.RUNNER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mentorhub.mail.MailDelivery;
import com.mentorhub.mail.MailDeliveryException;
import com.mentorhub.mail.MailMessage;
import com.mentorhub.support.Mailpit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Delivery against a real SMTP server. */
class JavaMailDeliveryMailServerTests {

    static final Mailpit mailpit = new Mailpit();

    @BeforeAll
    static void startMailpit() {
        mailpit.start();
    }

    @AfterAll
    static void stopMailpit() {
        mailpit.stop();
    }

    @BeforeEach
    void deleteMessages() {
        mailpit.deleteAll();
    }

    @Test
    void anInvalidRecipientIsADeliveryException() {
        mailServer("no-reply@example.org")
                .run(context -> assertThatThrownBy(() -> context.getBean(MailDelivery.class)
                                .send(new MailMessage("not an address", "Subject", "Text")))
                        .isInstanceOf(MailDeliveryException.class));

        assertThat(mailpit.awaitMessages(0)).isEmpty();
    }

    private static ApplicationContextRunner mailServer(String from) {
        return RUNNER.withPropertyValues(
                "spring.mail.host=" + mailpit.host(),
                "spring.mail.port=" + mailpit.smtpPort(),
                "mentorhub.mail.from=" + from);
    }
}
