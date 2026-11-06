package com.mentorhub.system.internal.dev;

import static org.assertj.core.api.Assertions.assertThat;

import com.mentorhub.TestcontainersConfiguration;
import com.mentorhub.system.internal.dev.Personas.Persona;
import com.mentorhub.users.Role;
import com.mentorhub.users.internal.domain.UserStatus;
import com.mentorhub.users.internal.security.TestIssuer;
import com.mentorhub.users.internal.security.TestIssuerConfiguration;
import com.mentorhub.users.internal.security.TestUsers;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * A start in development mode, simulated by {@link SampleCenter#restore()}. The personas sign in with tokens of the
 * local Keycloak's issuer, signed by {@link TestIssuer}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import({TestcontainersConfiguration.class, TestIssuerConfiguration.class, SampleCenterTests.LocalIssuer.class})
class SampleCenterTests {

    @Autowired
    SampleCenter sampleCenter;

    @Autowired
    MockMvcTester mvc;

    @Autowired
    TestIssuer issuer;

    @Autowired
    JdbcClient jdbc;

    TestUsers users;

    @BeforeEach
    void lastStart() {
        users = new TestUsers(jdbc);
        sampleCenter.restore();
    }

    @Test
    @DisplayName("DEV-1.2 A restart restores the configured access")
    void aRestartRestoresTheStatusAndExactlyTheRolesOfEveryPersona() {
        for (Persona persona : Personas.USERS) {
            changeStatusAndRoles(persona);
        }

        sampleCenter.restore();

        for (Persona persona : Personas.USERS) {
            assertThat(me(persona.subject()))
                    .bodyJson()
                    .extractingPath("$.status")
                    .as(persona.name())
                    .isEqualTo(persona.status());
            assertThat(roles(persona.subject()))
                    .as(persona.name())
                    .containsExactlyInAnyOrderElementsOf(persona.roles());
        }
    }

    @Test
    @DisplayName("DEV-1.3 A restart unlinks the persona Not invited")
    void aRestartUnlinksTheIdentityOfThePersonaNotInvited() {
        UUID user = users.create("linked-stranger@example.test", UserStatus.ACTIVE, Role.STUDENT);
        jdbc.sql("insert into user_identities (user_id, issuer, subject) values (:user, :issuer, :subject)")
                .param("user", user)
                .param("issuer", Personas.LOCAL_ISSUER)
                .param("subject", Personas.NOT_INVITED)
                .update();

        sampleCenter.restore();

        assertThat(me(Personas.NOT_INVITED)).bodyJson().isStrictlyEqualTo("{\"status\": \"NOT_INVITED\"}");
    }

    @Test
    @DisplayName("DEV-2.1 A start restores the sample center data")
    void aStartReplacesChangedDataWithTheSampleCenterData() {
        Map<String, List<String>> sampleCenterData = DatabaseSnapshot.withoutTimes(jdbc);
        UUID added = users.create("added@example.test", UserStatus.ACTIVE, Role.MENTOR);
        users.link(added, "added-subject");
        jdbc.sql("update users set first_name = 'Changed' where id <> :added")
                .param("added", added)
                .update();
        jdbc.sql("insert into audit_log (action, subject_type, subject_id) values ('USER_CREATED', 'USER', :id)")
                .param("id", added)
                .update();
        assertThat(DatabaseSnapshot.withoutTimes(jdbc)).isNotEqualTo(sampleCenterData);

        sampleCenter.restore();

        assertThat(DatabaseSnapshot.withoutTimes(jdbc)).isEqualTo(sampleCenterData);
    }

    // The realm and the sample center data are two sources of the personas; they must agree on the email.
    @Test
    void everyPersonaUserHasTheEmailOfItsRealmAccount() {
        Map<String, String> realmEmails = Personas.realmEmailsById();

        for (Persona persona : Personas.USERS) {
            assertThat(realmEmails).as(persona.name()).containsKey(persona.subject());
            assertThat(jdbc.sql("""
                            select u.email from users u join user_identities i on i.user_id = u.id
                            where i.issuer = :issuer and i.subject = :subject
                            """)
                            .param("issuer", Personas.LOCAL_ISSUER)
                            .param("subject", persona.subject())
                            .query(String.class)
                            .optional())
                    .as(persona.name())
                    .contains(realmEmails.get(persona.subject()));
        }
    }

    // Each persona user gets the other status and a role it should not have, instead of its own.
    private void changeStatusAndRoles(Persona persona) {
        String userOfPersona = "select user_id from user_identities where issuer = :issuer and subject = :subject";
        jdbc.sql("""
                        update users set
                            status = case when status = 'ACTIVE' then 'DISABLED' else 'ACTIVE' end,
                            disabled_reason = case when status = 'ACTIVE' then 'SECURITY' end,
                            disabled_comment = null
                        where id in (%s)
                        """.formatted(userOfPersona))
                .param("issuer", Personas.LOCAL_ISSUER)
                .param("subject", persona.subject())
                .update();
        jdbc.sql("delete from user_roles where user_id in (" + userOfPersona + ")")
                .param("issuer", Personas.LOCAL_ISSUER)
                .param("subject", persona.subject())
                .update();
        String foreignRole = persona.roles().contains("STUDENT") ? "OBSERVER" : "STUDENT";
        jdbc.sql("insert into user_roles (user_id, role) select user_id, :role from user_identities"
                        + " where issuer = :issuer and subject = :subject")
                .param("role", foreignRole)
                .param("issuer", Personas.LOCAL_ISSUER)
                .param("subject", persona.subject())
                .update();
    }

    private MvcTestResult me(String subject) {
        return mvc.get()
                .uri("/api/v1/me")
                .header(
                        HttpHeaders.AUTHORIZATION,
                        issuer.token(subject)
                                .claim("iss", Personas.LOCAL_ISSUER)
                                .bearer())
                .exchange();
    }

    private List<String> roles(String subject) {
        return jdbc.sql("""
                        select r.role from user_roles r join user_identities i on i.user_id = r.user_id
                        where i.issuer = :issuer and i.subject = :subject
                        """)
                .param("issuer", Personas.LOCAL_ISSUER)
                .param("subject", subject)
                .query(String.class)
                .list();
    }

    /** Trusts tokens of the local Keycloak's issuer that {@link TestIssuer} signs. */
    @TestConfiguration(proxyBeanMethods = false)
    static class LocalIssuer {

        @Bean
        DynamicPropertyRegistrar localIssuerProperties(TestIssuer issuer) {
            return registry -> {
                registry.add("mentorhub.security.trusted-issuers[3].issuer-uri", () -> Personas.LOCAL_ISSUER);
                registry.add("mentorhub.security.trusted-issuers[3].audience", () -> TestIssuer.AUDIENCE);
                registry.add("mentorhub.security.trusted-issuers[3].jwk-set-uri", issuer::jwkSetUri);
            };
        }
    }
}
