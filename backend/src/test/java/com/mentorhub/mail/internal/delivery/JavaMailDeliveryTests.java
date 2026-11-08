package com.mentorhub.mail.internal.delivery;

import static com.mentorhub.support.StartupFailures.assertStartupFailsNaming;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mentorhub.mail.MailDelivery;
import com.mentorhub.mail.MailDeliveryException;
import com.mentorhub.mail.MailMessage;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** The {@code mail} module (Design, Modules). */
class JavaMailDeliveryTests {

    static final MailMessage MESSAGE =
            new MailMessage("ivan@example.com", "You're invited to MentorHub", "Hello,\n\nGrüße – a line\n");

    static final ApplicationContextRunner RUNNER = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
            .withUserConfiguration(MailDeliveryConfiguration.class);

    @Test
    void isDisabledWithoutAMailHost() {
        RUNNER.run(context -> {
            MailDelivery delivery = context.getBean(MailDelivery.class);

            assertThat(delivery.isEnabled()).isFalse();
            assertThatThrownBy(() -> delivery.send(MESSAGE)).isInstanceOf(IllegalStateException.class);
        });
    }

    @Test
    void isDisabledWithoutAMailHostEvenWithASender() {
        RUNNER.withPropertyValues("mentorhub.mail.from=no-reply@example.org")
                .run(context -> assertThat(context.getBean(MailDelivery.class).isEnabled())
                        .isFalse());
    }

    @Test
    void isEnabledWithAMailHostAndASender() {
        RUNNER.withPropertyValues("spring.mail.host=127.0.0.1", "mentorhub.mail.from=MentorHub <no-reply@example.org>")
                .run(context -> assertThat(context.getBean(MailDelivery.class).isEnabled())
                        .isTrue());
    }

    @ParameterizedTest(name = "IEM-1.2 A mail server needs exactly one sender address: [{0}]")
    @DisplayName("IEM-1.2 A mail server needs exactly one sender address")
    @NullSource
    @ValueSource(strings = {"no-reply", "a@example.org, b@example.org"})
    @ExtendWith(OutputCaptureExtension.class)
    void aMailHostWithoutExactlyOneSenderFailsStartupNamingTheProperty(@Nullable String from, CapturedOutput output) {
        List<String> arguments = new ArrayList<>(List.of("--spring.mail.host=127.0.0.1"));
        if (from != null) {
            arguments.add("--mentorhub.mail.from=" + from);
        }

        assertStartupFailsNaming(output, MailDeliveryOnly.class, arguments, "mentorhub.mail.from");
    }

    @Test
    void anUnreachableMailServerIsADeliveryException() {
        // Port 9 (discard) is closed on the test machine, so the connection is refused at once.
        RUNNER.withPropertyValues(
                        "spring.mail.host=127.0.0.1",
                        "spring.mail.port=9",
                        "mentorhub.mail.from=MentorHub <no-reply@example.org>")
                .run(context -> assertThatThrownBy(
                                () -> context.getBean(MailDelivery.class).send(MESSAGE))
                        .isInstanceOf(MailDeliveryException.class));
    }

    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration(MailSenderAutoConfiguration.class)
    @Import(MailDeliveryConfiguration.class)
    static class MailDeliveryOnly {}
}
