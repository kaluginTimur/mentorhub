package com.mentorhub.users.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mentorhub.TestcontainersConfiguration;
import com.mentorhub.users.Role;
import com.mentorhub.users.internal.domain.UserChange;
import com.mentorhub.users.internal.domain.UserStatus;
import com.mentorhub.users.internal.security.TestAuthentications;
import com.mentorhub.users.internal.security.TestUsers;
import com.mentorhub.web.FieldViolation;
import com.mentorhub.web.ValidationException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.context.SecurityContextHolder;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ProfileServiceTests {

    @Autowired
    ProfileService profiles;

    @Autowired
    MeService me;

    @Autowired
    UserAdministrationService administration;

    @Autowired
    JdbcClient jdbc;

    TestUsers users;
    UUID caller;
    UUID other;

    @BeforeEach
    void setUp() {
        users = new TestUsers(jdbc);
        users.deleteAll();
        caller = users.create("noroles@x.test", UserStatus.ACTIVE);
        other = users.create("other@x.test", UserStatus.ACTIVE, Role.ADMIN);
        // A user without roles has no permissions and still edits their own profile.
        SecurityContextHolder.getContext().setAuthentication(TestAuthentications.activeUserAuthentication(caller));
    }

    @AfterEach
    void clearCaller() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("USR-9.2 A user's own profile change is audited with the user as the actor")
    void auditsWithTheCallerAsActor() {
        profiles.updateOwnProfile(new ProfileInput(" Ivan ", "Petrova", "8 701 123 45 67"));

        assertThat(administration.history(caller, 0, 25).items())
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.actor())
                            .isNotNull()
                            .extracting(HistoryActor::id)
                            .isEqualTo(caller);
                    assertThat(entry.change()).isInstanceOf(UserChange.ProfileUpdated.class);
                });
    }

    @Test
    void neverChangesAnotherUser() {
        profiles.updateOwnProfile(new ProfileInput("Ivan", null, null));

        assertThat(jdbc.sql("select first_name from users where id = :id")
                        .param("id", other)
                        .query(String.class)
                        .single())
                .isEqualTo("Anna");
    }

    @Test
    void invalidValuesStoreNothing() {
        assertThatThrownBy(() -> profiles.updateOwnProfile(new ProfileInput("Ivan", null, "not a phone")))
                .isInstanceOfSatisfying(
                        ValidationException.class,
                        exception -> assertThat(exception.errors())
                                .containsExactly(new FieldViolation("phone", "must be a valid phone number")));

        assertThat(me.me().user().firstName()).isEqualTo("Anna");
        assertThat(users.count("audit_log")).isZero();
    }
}
