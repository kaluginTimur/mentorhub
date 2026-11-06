package com.mentorhub.system.internal.dev;

import static org.assertj.core.api.Assertions.assertThat;

import com.mentorhub.TestcontainersConfiguration;
import com.mentorhub.users.internal.security.TestIssuerConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/**
 * The clean drops the event publication table, which Flyway does not create; the restore must leave it in place and
 * empty, so no publication from before a start is delivered against the new data. Same context as
 * {@link SampleCenterTests}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Import({TestcontainersConfiguration.class, TestIssuerConfiguration.class, SampleCenterTests.LocalIssuer.class})
class SampleCenterEventPublicationTests {

    @Autowired
    SampleCenter sampleCenter;

    @Autowired
    JdbcClient jdbc;

    @Test
    void theRestoreLeavesAnEmptyEventPublicationTable() {
        jdbc.sql("""
                        insert into event_publication (id, listener_id, event_type, serialized_event, publication_date)
                        values (:id, 'listener', 'com.example.Event', '{}', now())
                        """).param("id", UUID.randomUUID()).update();

        sampleCenter.restore();

        assertThat(jdbc.sql("select count(*) from event_publication")
                        .query(Long.class)
                        .single())
                .isZero();
    }
}
