package com.mentorhub.system.internal.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.mentorhub.mail.MailDelivery;
import com.mentorhub.users.internal.security.WebSliceSecurity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(
        controllers = SystemController.class,
        properties = {
            "mentorhub.security.client.issuer-uri=http://localhost:8180/realms/mentorhub",
            "mentorhub.security.client.client-id=mentorhub-web"
        })
@Import({WebSliceSecurity.class, SystemWebConfiguration.class})
class SystemControllerTest {

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    MailDelivery mail;

    @Test
    void returnsNameAndVersion() {
        assertThat(mvc.get().uri("/api/v1/system/info"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.name")
                .isEqualTo("MentorHub");
    }

    @Test
    void returnsClientConfigurationToAnyone() {
        assertThat(mvc.get().uri("/api/v1/system/client-config"))
                .hasStatusOk()
                .bodyJson()
                .isStrictlyEqualTo("""
                        {
                          "issuer": "http://localhost:8180/realms/mentorhub",
                          "clientId": "mentorhub-web",
                          "scope": "openid profile email",
                          "invitationEmails": false
                        }
                        """);
    }

    // invitation-emails AC-1
    @Test
    void reportsEnabledEmail() {
        given(mail.isEnabled()).willReturn(true);

        assertThat(mvc.get().uri("/api/v1/system/client-config"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.invitationEmails")
                .isEqualTo(true);
    }

    // AC-24
    @Test
    void publicEndpointsIgnoreAnInvalidToken() {
        for (String path : new String[] {"/api/v1/system/info", "/api/v1/system/client-config"}) {
            assertThat(mvc.get().uri(path).header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                    .hasStatusOk();
        }
    }
}
