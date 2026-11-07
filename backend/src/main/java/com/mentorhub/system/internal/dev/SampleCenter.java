package com.mentorhub.system.internal.dev;

import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

/**
 * Brings the dev database back to the sample center data (DEV-1, DEV-2): drops everything, migrates, and inserts the
 * sample data. Writes no audit entries of its own and publishes no events; the audit history is part of the sample
 * data.
 *
 * <p>Tests: {@code SampleCenterTests} runs the restore as a start (DEV-1.2, DEV-1.3, DEV-2.1) and checks that the
 * persona users have the emails of their realm accounts; {@code DevelopmentRealmConsistencyTests} checks the realm
 * itself; {@code SampleCenterDataTests} reads the whole data set back through the application;
 * {@code SampleCenterEventPublicationTests} checks the event publication table; {@code OutsideDevelopmentModeTests}
 * checks that a start without {@code dev} changes nothing (DEV-1.1, DEV-2.2).
 */
@Component
@Profile("dev")
class SampleCenter {

    private static final Logger log = LoggerFactory.getLogger(SampleCenter.class);

    /**
     * One script per module, in an order that satisfies the foreign keys. Plain SQL rather than calls to the services,
     * so the database holds exactly the rows the scripts list. They are never Flyway migrations: {@code db/sample} is
     * not on {@code spring.flyway.locations}.
     */
    private static final List<String> SAMPLE_DATA = List.of("db/sample/users.sql");

    /**
     * Modulith creates the event publication table itself, not Flyway, and the clean drops it. Whether Modulith's
     * initializer runs before or after the restore on start is not ordered, so the restore creates the table with
     * Modulith's own script ({@code CREATE ... IF NOT EXISTS}), empty. The path follows the schema version of
     * spring-modulith-events-jdbc 2.x: a Modulith upgrade that moves to a new schema version needs it changed, and
     * until then the missing script fails every start in {@code dev} and {@code SampleCenterEventPublicationTests}.
     */
    private static final String EVENT_PUBLICATION_SCHEMA =
            "org/springframework/modulith/events/jdbc/schemas/v2/schema-postgresql.sql";

    private final Flyway flyway;
    private final DataSource dataSource;

    SampleCenter(Flyway flyway, DataSource dataSource) {
        this.flyway = flyway;
        this.dataSource = dataSource;
    }

    public void restore() {
        flyway.clean();
        flyway.migrate();
        ResourceDatabasePopulator scripts =
                new ResourceDatabasePopulator(new ClassPathResource(EVENT_PUBLICATION_SCHEMA));
        SAMPLE_DATA.forEach(script -> scripts.addScript(new ClassPathResource(script)));
        scripts.execute(dataSource);
        log.info("The dev database holds the sample center data");
    }
}
