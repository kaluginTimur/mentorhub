package com.mentorhub.system.internal.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.mentorhub.mail.MailDelivery;
import com.mentorhub.users.internal.security.WebSliceSecurity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

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
    @DisplayName("PLT-4.1 Name, version and configuration are public")
    void nameVersionAndConfigurationArePublic() {
        MvcTestResult info = mvc.get().uri("/api/v1/system/info").exchange();
        assertThat(info).hasStatusOk();
        assertThat(info).bodyJson().extractingPath("$.name").isEqualTo("MentorHub");
        assertThat(info).bodyJson().extractingPath("$.version").asString().isNotBlank();

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

    @Test
    void reportsEnabledEmail() {
        given(mail.isEnabled()).willReturn(true);

        assertThat(mvc.get().uri("/api/v1/system/client-config"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.invitationEmails")
                .isEqualTo(true);
    }

    @Test
    @DisplayName("PLT-4.2 An invalid token does not fail a public request")
    void publicEndpointsIgnoreAnInvalidToken() {
        for (String path : new String[] {"/api/v1/system/info", "/api/v1/system/client-config"}) {
            assertThat(mvc.get().uri(path).header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                    .hasStatusOk();
        }
    }
}
