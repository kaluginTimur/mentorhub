package com.mentorhub;

import static org.assertj.core.api.Assertions.assertThat;

import com.mentorhub.mail.MailDelivery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Spec invitation-emails: the default configuration has no mail server, and Modulith creates the registry's table. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class InfrastructureConfigurationTests {

    @Autowired
    MailDelivery mail;

    @Autowired
    JdbcClient jdbc;

    // AC-1
    @Test
    void emailIsDisabledWithoutAMailHost() {
        assertThat(mail.isEnabled()).isFalse();
    }

    // Design, Asynchronous delivery: schema initialization, not Flyway.
    @Test
    void theEventPublicationRegistryHasItsTable() {
        assertThat(jdbc.sql("select to_regclass('event_publication') is not null")
                        .query(Boolean.class)
                        .single())
                .isTrue();
        assertThat(jdbc.sql("select count(*) from flyway_schema_history where script ilike '%event%'")
                        .query(Long.class)
                        .single())
                .isZero();
    }
}
