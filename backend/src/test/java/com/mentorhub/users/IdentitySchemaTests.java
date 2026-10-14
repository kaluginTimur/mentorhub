package com.mentorhub.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mentorhub.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class IdentitySchemaTests {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void seedsAllRoles() {
        assertThat(jdbc.queryForList("select name from roles", String.class))
                .containsExactlyInAnyOrder("ADMIN", "COORDINATOR", "MENTOR", "STUDENT", "OBSERVER");
    }

    @Test
    void emailIsUniqueIgnoringCase() {
        insertUser("Anna@Example.com", "ACTIVE");

        assertThatThrownBy(() -> insertUser("anna@example.COM", "ACTIVE")).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void emailIsStoredAsReceived() {
        UUID id = insertUser("Anna@Example.com", "ACTIVE");

        assertThat(jdbc.queryForObject("select email from users where id = ?", String.class, id))
                .isEqualTo("Anna@Example.com");
    }

    @Test
    void namesAreOptional() {
        UUID id = insertUser("anna@example.com", "ACTIVE");

        assertThat(jdbc.queryForObject(
                        "select first_name is null and last_name is null from users where id = ?", Boolean.class, id))
                .isTrue();
    }

    @Test
    void rejectsEmptyEmail() {
        assertThatThrownBy(() -> insertUser("", "ACTIVE")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsUnknownStatus() {
        assertThatThrownBy(() -> insertUser("anna@example.com", "PENDING"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void identityIsUniquePerIssuerAndSubject() {
        UUID anna = insertUser("anna@example.com", "ACTIVE");
        UUID boris = insertUser("boris@example.com", "ACTIVE");
        insertIdentity(anna, "https://idp.example/realms/a", "sub-1");
        insertIdentity(anna, "https://idp.example/realms/b", "sub-1");

        assertThatThrownBy(() -> insertIdentity(boris, "https://idp.example/realms/a", "sub-1"))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void identityRequiresExistingUser() {
        assertThatThrownBy(() -> insertIdentity(UUID.randomUUID(), "https://idp.example/realms/a", "sub-1"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void userWithIdentityCannotBeDeleted() {
        UUID anna = insertUser("anna@example.com", "ACTIVE");
        insertIdentity(anna, "https://idp.example/realms/a", "sub-1");

        assertThatThrownBy(() -> jdbc.update("delete from users where id = ?", anna))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void roleAssignmentRequiresKnownRole() {
        UUID anna = insertUser("anna@example.com", "ACTIVE");

        assertThatThrownBy(() -> jdbc.update("insert into user_roles (user_id, role) values (?, ?)", anna, "SUPERUSER"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void roleIsAssignedOncePerUser() {
        UUID anna = insertUser("anna@example.com", "ACTIVE");
        jdbc.update("insert into user_roles (user_id, role) values (?, 'ADMIN')", anna);

        assertThatThrownBy(() -> jdbc.update("insert into user_roles (user_id, role) values (?, 'ADMIN')", anna))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void assignedRoleCannotBeDeleted() {
        UUID anna = insertUser("anna@example.com", "ACTIVE");
        jdbc.update("insert into user_roles (user_id, role) values (?, 'OBSERVER')", anna);

        assertThatThrownBy(() -> jdbc.update("delete from roles where name = 'OBSERVER'"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID insertUser(String email, String status) {
        return jdbc.queryForObject(
                "insert into users (email, status) values (?, ?) returning id", UUID.class, email, status);
    }

    private void insertIdentity(UUID userId, String issuer, String subject) {
        jdbc.update("insert into user_identities (user_id, issuer, subject) values (?, ?, ?)", userId, issuer, subject);
    }
}
