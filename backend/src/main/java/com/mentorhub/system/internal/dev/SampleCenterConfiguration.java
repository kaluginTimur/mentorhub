package com.mentorhub.system.internal.dev;

import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("dev")
class SampleCenterConfiguration {

    /** Replaces the ordinary migration on start, so the restore runs before anything else uses the database. */
    @Bean
    FlywayMigrationStrategy sampleCenterOnStart(SampleCenter sampleCenter) {
        return flyway -> sampleCenter.restore();
    }
}
