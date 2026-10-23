package com.mentorhub.mail.internal.delivery;

import static com.mentorhub.mail.internal.delivery.JavaMailDeliveryTests.MESSAGE;
import static com.mentorhub.mail.internal.delivery.JavaMailDeliveryTests.RUNNER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mentorhub.mail.MailDelivery;
import com.mentorhub.mail.MailDeliveryException;
import com.mentorhub.mail.MailMessage;
import com.mentorhub.support.Mailpit;
import com.mentorhub.support.Mailpit.Message;
import java.util.List;
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
    void sendsAPlainTextMessageFromTheSender() {
        mailServer("MentorHub Schüler <no-reply@example.org>")
                .run(context -> context.getBean(MailDelivery.class).send(MESSAGE));

        List<Message> messages = mailpit.awaitMessages(1);
        assertThat(messages).hasSize(1);
        Message message = messages.getFirst();
        assertThat(message.from()).isEqualTo("no-reply@example.org");
        assertThat(message.fromName()).isEqualTo("MentorHub Schüler");
        assertThat(message.to()).containsExactly("ivan@example.com");
        assertThat(message.subject()).isEqualTo("You're invited to MentorHub");
        assertThat(message.text().replace("\r\n", "\n")).isEqualTo(MESSAGE.text());
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
