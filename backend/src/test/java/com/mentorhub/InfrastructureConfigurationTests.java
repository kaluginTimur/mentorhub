package com.mentorhub;

import static org.assertj.core.api.Assertions.assertThat;

import com.mentorhub.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Spec invitation-emails: Modulith creates the registry's table. */
@IntegrationTest
class InfrastructureConfigurationTests {

    @Autowired
    JdbcClient jdbc;

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
