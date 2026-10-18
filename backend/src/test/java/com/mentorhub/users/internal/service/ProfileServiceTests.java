package com.mentorhub.users.internal.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mentorhub.TestcontainersConfiguration;
import com.mentorhub.users.Role;
import com.mentorhub.users.internal.domain.UserStatus;
import com.mentorhub.users.internal.security.TestAuthentications;
import com.mentorhub.users.internal.security.TestUsers;
import com.mentorhub.web.FieldViolation;
import com.mentorhub.web.ValidationException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.context.SecurityContextHolder;

// AC-17
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ProfileServiceTests {

    @Autowired
    ProfileService profiles;

    @Autowired
    MeService me;

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
    void storesTheCallersOwnProfileAndAuditsWithTheCallerAsActor() {
        profiles.updateOwnProfile(new ProfileInput(" Ivan ", "Petrova", "8 701 123 45 67"));

        UserProfile profile = me.me().user();
        assertThat(profile).isNotNull();
        assertThat(profile.firstName()).isEqualTo("Ivan");
        assertThat(profile.lastName()).isEqualTo("Petrova");
        assertThat(profile.phone()).isEqualTo("+77011234567");
        assertThat(profile.email()).isEqualTo("noroles@x.test");
        assertThat(jdbc.sql("select action, actor_user_id, subject_id from audit_log")
                        .query()
                        .singleRow())
                .containsEntry("action", "USER_PROFILE_UPDATED")
                .containsEntry("actor_user_id", caller)
                .containsEntry("subject_id", caller);
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
    void unchangedValuesWriteNoEntry() {
        profiles.updateOwnProfile(new ProfileInput("Anna", " Petrova ", ""));

        assertThat(users.count("audit_log")).isZero();
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
