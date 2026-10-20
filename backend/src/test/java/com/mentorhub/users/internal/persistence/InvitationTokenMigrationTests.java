package com.mentorhub.users.internal.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Spec invitation-links, AC-6: V5 gives every invitation stored with a token hash a new plaintext token. */
@Testcontainers
class InvitationTokenMigrationTests {

    // Its own empty database: the application's test database is already migrated to the latest version.
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:18"));

    private record Row(UUID id, String token) {}

    @Test
    void givesEveryExistingInvitationANewTokenInTheApplicationFormat() {
        JdbcClient jdbc = JdbcClient.create(
                new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        migrate("4");

        UUID admin = jdbc.sql("insert into users (email, status) values ('admin@x.test', 'ACTIVE') returning id")
                .query(UUID.class)
                .single();
        UUID pending = insert(jdbc, "pending@x.test", admin, null);
        UUID accepted = insert(jdbc, "accepted@x.test", admin, "accepted_at = now(), accepted_user_id = created_by");
        UUID revoked = insert(jdbc, "revoked@x.test", admin, "revoked_at = now(), revoked_by = created_by");
        jdbc.sql("insert into invitation_roles (invitation_id, role) values (:id, 'MENTOR')")
                .param("id", pending)
                .update();

        migrate(null);

        Map<UUID, String> tokens = jdbc
                .sql("select id, token from invitations")
                .query((rs, rowNum) -> new Row(rs.getObject("id", UUID.class), rs.getString("token")))
                .list()
                .stream()
                .collect(Collectors.toMap(Row::id, Row::token));
        assertThat(tokens).containsOnlyKeys(pending, accepted, revoked);
        assertThat(tokens.values()).allMatch(token -> token.matches("[A-Za-z0-9_-]{43}"));
        assertThat(tokens.values().stream().distinct()).hasSize(3);
        assertThat(jdbc.sql("""
                        select count(*) from information_schema.columns
                        where table_name = 'invitations' and column_name = 'token_hash'
                        """).query(Integer.class).single()).isZero();
        assertThat(jdbc.sql("select role from invitation_roles where invitation_id = :id")
                        .param("id", pending)
                        .query(String.class)
                        .list())
                .containsExactly("MENTOR");
        assertThat(jdbc.sql("""
                        select email from invitations
                        where accepted_at is null and revoked_at is null and expires_at > now()
                        """).query(String.class).list()).containsExactly("pending@x.test");
    }

    private static UUID insert(JdbcClient jdbc, String email, UUID createdBy, @Nullable String closing) {
        UUID id = jdbc.sql("""
                        insert into invitations (email, token_hash, created_by, expires_at)
                        values (:email, :hash, :createdBy, now() + interval '7 days')
                        returning id
                        """)
                .param("email", email)
                .param("hash", randomHash())
                .param("createdBy", createdBy)
                .query(UUID.class)
                .single();
        if (closing != null) {
            jdbc.sql("update invitations set " + closing + " where id = :id")
                    .param("id", id)
                    .update();
        }
        return id;
    }

    private static void migrate(@Nullable String target) {
        FluentConfiguration configuration = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration");
        if (target != null) {
            configuration.target(target);
        }
        configuration.load().migrate();
    }

    private static String randomHash() {
        byte[] bytes = new byte[32];
        ThreadLocalRandom.current().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
