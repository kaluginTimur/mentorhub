package com.mentorhub.users.internal.security;

import com.mentorhub.users.Role;
import com.mentorhub.users.internal.domain.UserStatus;
import java.sql.Types;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Direct database setup for authentication tests; they run against committed data, not a test transaction. */
public final class TestUsers {

    private final JdbcClient jdbc;

    public TestUsers(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void deleteAll() {
        // The append-only trigger rejects delete but not truncate.
        jdbc.sql("truncate audit_log").update();
        jdbc.sql("delete from invitation_roles").update();
        jdbc.sql("delete from invitations").update();
        jdbc.sql("delete from user_roles").update();
        jdbc.sql("delete from user_identities").update();
        jdbc.sql("delete from users").update();
        jdbc.sql("delete from roles where name not in (:names)")
                .param("names", Arrays.stream(Role.values()).map(Role::name).toList())
                .update();
    }

    public UUID create(String email, UserStatus status, Role... roles) {
        UUID id = jdbc.sql("insert into users (email, first_name, last_name, status, disabled_reason) values"
                        + " (:email, 'Anna', 'Petrova', :status, :reason) returning id")
                .param("email", email)
                .param("status", status.name())
                .param("reason", status == UserStatus.DISABLED ? "SECURITY" : null, Types.VARCHAR)
                .query(UUID.class)
                .single();
        for (Role role : roles) {
            assignStoredRole(id, role.name());
        }
        return id;
    }

    public void assignStoredRole(UUID userId, String role) {
        jdbc.sql("insert into user_roles (user_id, role) values (:userId, :role)")
                .param("userId", userId)
                .param("role", role)
                .update();
    }

    public void link(UUID userId, String subject) {
        jdbc.sql("insert into user_identities (user_id, issuer, subject) values (:userId, :issuer, :subject)")
                .param("userId", userId)
                .param("issuer", TestIssuer.ISSUER)
                .param("subject", subject)
                .update();
    }

    public int count(String table) {
        return jdbc.sql("select count(*) from " + table).query(Integer.class).single();
    }

    public List<String> roles(UUID userId) {
        return jdbc.sql("select role from user_roles where user_id = :userId order by role")
                .param("userId", userId)
                .query(String.class)
                .list();
    }

    public String status(UUID userId) {
        return jdbc.sql("select status from users where id = :id")
                .param("id", userId)
                .query(String.class)
                .single();
    }
}
