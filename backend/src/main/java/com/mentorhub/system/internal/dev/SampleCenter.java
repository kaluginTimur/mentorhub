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
 * sample data. Writes no audit entries and publishes no events, so the data is exactly the sample data.
 */
@Component
@Profile("dev")
class SampleCenter {

    private static final Logger log = LoggerFactory.getLogger(SampleCenter.class);

    /** One script per module, in an order that satisfies the foreign keys. */
    private static final List<String> SAMPLE_DATA = List.of("db/sample/users.sql");

    /**
     * Modulith creates the event publication table itself, not Flyway, and the clean drops it. Whether Modulith's
     * initializer runs before or after the restore on start is not ordered, so the restore creates the table with
     * Modulith's own script ({@code CREATE ... IF NOT EXISTS}), empty. The path follows the schema version of
     * spring-modulith-events-jdbc 2.x; a missing script fails the start.
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
