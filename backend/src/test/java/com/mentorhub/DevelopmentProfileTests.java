package com.mentorhub;

import static org.assertj.core.api.Assertions.assertThat;

import com.mentorhub.mail.MailDelivery;
import com.mentorhub.users.internal.security.TestIssuerConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * The dev profile serves Swagger UI, the API docs, the contract and the actuator info and modulith endpoints, and sends
 * email to Mailpit.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import({TestcontainersConfiguration.class, TestIssuerConfiguration.class})
class DevelopmentProfileTests {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    MailDelivery mail;

    @Test
    void developmentPathsAreServedWithoutSignIn() {
        assertThat(mvc.get().uri("/openapi.yaml")).hasStatusOk();
        assertThat(mvc.get().uri("/v3/api-docs/swagger-config")).hasStatusOk();
        assertThat(mvc.get().uri("/swagger-ui.html")).hasStatus3xxRedirection();
        assertThat(mvc.get().uri("/actuator/info")).hasStatusOk();
        assertThat(mvc.get().uri("/actuator/modulith")).hasStatusOk();
    }

    // Spec invitation-emails, Configuration: the dev profile sends to Mailpit, which these tests do not start. An
    // unreachable mail server must not make the application unhealthy.
    @Test
    void emailIsEnabledAndAnUnreachableMailServerKeepsTheApplicationHealthy() {
        assertThat(mail.isEnabled()).isTrue();
        assertThat(mvc.get().uri("/actuator/health")).hasStatusOk();
    }

    @Test
    void developmentPathsIgnoreAStaleToken() {
        assertThat(mvc.get().uri("/openapi.yaml").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .hasStatusOk();
    }
}
