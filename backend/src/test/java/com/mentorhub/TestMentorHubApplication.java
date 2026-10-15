package com.mentorhub;

import java.util.stream.Stream;
import org.springframework.boot.SpringApplication;

/**
 * Runs the application in the dev profile with a throwaway Postgres from Testcontainers. Docker Compose support is
 * off, so neither the dev database nor Keycloak from {@code infra/compose.yaml} is started; sign-in needs Keycloak
 * running ({@code docker compose -f infra/compose.yaml up -d keycloak}).
 */
public class TestMentorHubApplication {

    public static void main(String[] args) {
        SpringApplication.from(MentorHubApplication::main)
                .with(TestcontainersConfiguration.class)
                .withAdditionalProfiles("dev")
                .run(Stream.concat(Stream.of("--spring.docker.compose.enabled=false"), Stream.of(args))
                        .toArray(String[]::new));
    }
}
