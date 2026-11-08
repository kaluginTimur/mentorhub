package com.mentorhub.users.internal.web;

import static com.mentorhub.users.internal.security.ProblemAssertions.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;

import com.mentorhub.support.EndpointTest;
import com.mentorhub.users.Role;
import com.mentorhub.users.internal.domain.UserStatus;
import com.mentorhub.users.internal.security.TestIssuer;
import com.mentorhub.users.internal.security.TestUsers;
import com.mentorhub.users.internal.service.LockoutProblems;
import com.mentorhub.web.Problems;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * User administration through the real chain: signed tokens, the gate, permissions expanded from stored roles, method
 * validation, the services and the database.
 */
@EndpointTest
class UsersEndpointTests {

    static final String PROFILE_BODY = """
            {"firstName": "  Ivan ", "lastName": "Sidorov", "phone": "8 701 123 45 67"}""";

    @Autowired
    MockMvcTester mvc;

    @Autowired
    TestIssuer issuer;

    @Autowired
    JdbcClient jdbc;

    TestUsers users;
    UUID admin;
    UUID mentor;

    @BeforeEach
    void setUp() {
        users = new TestUsers(jdbc);
        users.deleteAll();
        admin = users.create("admin@x.test", UserStatus.ACTIVE, Role.ADMIN);
        users.link(admin, "admin");
        mentor = users.create("mentor@x.test", UserStatus.ACTIVE, Role.MENTOR);
        users.link(mentor, "mentor");
    }

    @Test
    void listsAndFiltersUsers() {
        UUID withoutRoles = users.create("maria@x.test", UserStatus.ACTIVE);

        MvcTestResult all = send(HttpMethod.GET, "/api/v1/users", "", "admin");
        assertThat(all).hasStatusOk();
        assertThat(all).bodyJson().extractingPath("$.totalItems").isEqualTo(3);
        assertThat(all).bodyJson().extractingPath("$.size").isEqualTo(25);

        MvcTestResult none = send(HttpMethod.GET, "/api/v1/users?role=NONE", "", "admin");
        assertThat(none).bodyJson().extractingPath("$.items[*].id").asArray().containsExactly(withoutRoles.toString());

        MvcTestResult search = send(HttpMethod.GET, "/api/v1/users?q=MENTOR@&status=ACTIVE", "", "admin");
        assertThat(search).bodyJson().extractingPath("$.items[*].id").asArray().containsExactly(mentor.toString());
    }

    @Test
    @DisplayName("PLT-3.1 Defaults apply when no paging is requested")
    void listsTheFirst25UsersByDefault() {
        for (int i = 0; i < 30; i++) {
            users.create("extra%02d@x.test".formatted(i), UserStatus.ACTIVE);
        }

        MvcTestResult result = send(HttpMethod.GET, "/api/v1/users", "", "admin");

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.items").asArray().hasSize(25);
        assertThat(result).bodyJson().extractingPath("$.page").isEqualTo(0);
        assertThat(result).bodyJson().extractingPath("$.size").isEqualTo(25);
        assertThat(result).bodyJson().extractingPath("$.totalItems").isEqualTo(32);
    }

    @Test
    void unknownUserIsNotFound() {
        assertProblem(
                send(HttpMethod.GET, "/api/v1/users/" + UUID.randomUUID(), "", "admin"),
                HttpStatus.NOT_FOUND,
                Problems.NOT_FOUND);
    }

    @Test
    void disabledUserIsRefusedOnTheNextRequestAndEnabledAgainWithItsHistory() {
        MvcTestResult disabled = send(
                HttpMethod.POST,
                "/api/v1/users/" + mentor + "/disable",
                "{\"reason\": \"GRADUATED\", \"comment\": \"  Finished the spring cohort  \"}",
                "admin");

        assertThat(disabled).hasStatusOk();
        assertThat(disabled).bodyJson().extractingPath("$.status").isEqualTo("DISABLED");
        assertThat(disabled).bodyJson().extractingPath("$.disabledReason").isEqualTo("GRADUATED");
        assertThat(disabled).bodyJson().extractingPath("$.disabledComment").isEqualTo("Finished the spring cohort");
        assertProblem(
                send(HttpMethod.PUT, "/api/v1/me/profile", PROFILE_BODY, "mentor"),
                HttpStatus.FORBIDDEN,
                Problems.ACCESS_ENDED);

        MvcTestResult enabled = send(HttpMethod.POST, "/api/v1/users/" + mentor + "/enable", "", "admin");

        assertThat(enabled).hasStatusOk();
        assertThat(enabled).bodyJson().extractingPath("$.status").isEqualTo("ACTIVE");
        assertThat(enabled).bodyJson().doesNotHavePath("$.disabledReason").doesNotHavePath("$.disabledComment");
        assertThat(enabled).bodyJson().extractingPath("$.roles").asArray().containsExactly("MENTOR");
        assertThat(send(HttpMethod.GET, "/api/v1/me", "", "mentor"))
                .bodyJson()
                .extractingPath("$.status")
                .isEqualTo("ACTIVE");

        MvcTestResult history = send(HttpMethod.GET, "/api/v1/users/" + mentor + "/history", "", "admin");
        assertThat(history).hasStatusOk();
        assertThat(history).bodyJson().extractingPath("$.totalItems").isEqualTo(2);
        assertThat(history)
                .bodyJson()
                .extractingPath("$.items[*].action")
                .asArray()
                .containsExactlyInAnyOrder("USER_ENABLED", "USER_DISABLED");
        assertThat(history)
                .bodyJson()
                .extractingPath("$.items[*].actor.name")
                .asArray()
                .containsOnly("Anna Petrova");
        assertThat(history)
                .bodyJson()
                .extractingPath("$.items[*].comment")
                .asArray()
                .containsOnly("Finished the spring cohort");
        assertThat(history).bodyJson().doesNotHavePath("$.items[0].rolesAdded");
    }

    // Role changes take effect on the user's next request.
    @Test
    @DisplayName("USR-6.2 An empty set leaves an active user without roles")
    void roleChangesTakeEffectOnTheNextRequest() {
        assertProblem(send(HttpMethod.GET, "/api/v1/users", "", "mentor"), HttpStatus.FORBIDDEN, Problems.FORBIDDEN);

        MvcTestResult granted = send(
                HttpMethod.PUT, "/api/v1/users/" + mentor + "/roles", "{\"roles\": [\"ADMIN\", \"MENTOR\"]}", "admin");

        assertThat(granted).hasStatusOk();
        assertThat(granted).bodyJson().extractingPath("$.roles").asArray().containsExactly("ADMIN", "MENTOR");
        assertThat(send(HttpMethod.GET, "/api/v1/users", "", "mentor")).hasStatusOk();

        MvcTestResult removed = send(HttpMethod.PUT, "/api/v1/users/" + mentor + "/roles", "{\"roles\": []}", "admin");

        assertThat(removed).hasStatusOk();
        assertThat(removed).bodyJson().extractingPath("$.status").isEqualTo("ACTIVE");
        assertThat(removed).bodyJson().extractingPath("$.roles").asArray().isEmpty();
        MvcTestResult me = send(HttpMethod.GET, "/api/v1/me", "", "mentor");
        assertThat(me).bodyJson().extractingPath("$.status").isEqualTo("ACTIVE");
        assertThat(me).bodyJson().extractingPath("$.user.permissions").asArray().isEmpty();
        assertProblem(send(HttpMethod.GET, "/api/v1/users", "", "mentor"), HttpStatus.FORBIDDEN, Problems.FORBIDDEN);

        MvcTestResult history = send(HttpMethod.GET, "/api/v1/users/" + mentor + "/history", "", "admin");
        assertThat(history).bodyJson().extractingPath("$.totalItems").isEqualTo(2);
        assertThat(history)
                .bodyJson()
                .extractingPath("$.items[*].action")
                .asArray()
                .containsOnly("USER_ROLES_CHANGED");
    }

    // The API answers lockout with 409 and changes nothing.
    @Test
    @DisplayName("USR-7.1 An administrator cannot disable themselves")
    void administratorCannotDisableThemselves() {
        MvcTestResult disable =
                send(HttpMethod.POST, "/api/v1/users/" + admin + "/disable", "{\"reason\": \"SECURITY\"}", "admin");

        assertProblem(disable, HttpStatus.CONFLICT, LockoutProblems.SELF_LOCKOUT);
        assertThat(disable).bodyJson().extractingPath("$.detail").isEqualTo("You can't lock yourself out.");
        assertThat(users.status(admin)).isEqualTo("ACTIVE");
        assertThat(users.roles(admin)).containsExactly("ADMIN");
        assertThat(users.count("audit_log")).isZero();
    }

    @Test
    @DisplayName("USR-7.3 An administrator cannot remove their own administrator role")
    void administratorCannotRemoveTheirOwnAdministratorRole() {
        MvcTestResult roles =
                send(HttpMethod.PUT, "/api/v1/users/" + admin + "/roles", "{\"roles\": [\"MENTOR\"]}", "admin");

        assertProblem(roles, HttpStatus.CONFLICT, LockoutProblems.SELF_LOCKOUT);
        assertThat(users.status(admin)).isEqualTo("ACTIVE");
        assertThat(users.roles(admin)).containsExactly("ADMIN");
        assertThat(users.count("audit_log")).isZero();
    }

    @Test
    @DisplayName("AUD-4.1 A refused change writes no entry")
    void aRefusedChangeWritesNoEntry() {
        MvcTestResult disable =
                send(HttpMethod.POST, "/api/v1/users/" + admin + "/disable", "{\"reason\": \"SECURITY\"}", "admin");

        assertProblem(disable, HttpStatus.CONFLICT, LockoutProblems.SELF_LOCKOUT);
        assertThat(users.count("audit_log")).isZero();
    }

    @Test
    void administratorUpdatesAnotherUsersProfile() {
        MvcTestResult result = send(HttpMethod.PUT, "/api/v1/users/" + mentor + "/profile", PROFILE_BODY, "admin");

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.firstName").isEqualTo("Ivan");
        assertThat(result).bodyJson().extractingPath("$.phone").isEqualTo("+77011234567");
        MvcTestResult history = send(HttpMethod.GET, "/api/v1/users/" + mentor + "/history", "", "admin");
        assertThat(history).bodyJson().extractingPath("$.items[0].action").isEqualTo("USER_PROFILE_UPDATED");
        assertThat(history)
                .bodyJson()
                .extractingPath("$.items[0].changes[*].field")
                .asArray()
                .containsExactlyInAnyOrder("firstName", "lastName", "phone");
    }

    @Test
    @DisplayName("USR-9.1 A user without roles saves their own profile")
    void userWithoutRolesUpdatesTheirOwnProfile() {
        UUID maria = users.create("maria@x.test", UserStatus.ACTIVE);
        users.link(maria, "maria");

        MvcTestResult result = send(HttpMethod.PUT, "/api/v1/me/profile", PROFILE_BODY, "maria");

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.user.id").isEqualTo(maria.toString());
        assertThat(result).bodyJson().extractingPath("$.user.firstName").isEqualTo("Ivan");
        assertThat(result).bodyJson().extractingPath("$.user.lastName").isEqualTo("Sidorov");
        assertThat(result).bodyJson().extractingPath("$.user.phone").isEqualTo("+77011234567");
        MvcTestResult me = send(HttpMethod.GET, "/api/v1/me", "", "maria");
        assertThat(me).bodyJson().extractingPath("$.user.firstName").isEqualTo("Ivan");
        assertThat(me).bodyJson().extractingPath("$.user.lastName").isEqualTo("Sidorov");
        assertThat(me).bodyJson().extractingPath("$.user.phone").isEqualTo("+77011234567");
        assertThat(users.count("audit_log")).isEqualTo(1);
    }

    @Test
    void invalidOwnProfileStoresNothing() {
        MvcTestResult result = send(
                HttpMethod.PUT,
                "/api/v1/me/profile",
                "{\"firstName\": \"%s\", \"lastName\": null, \"phone\": \"12\"}".formatted("x".repeat(101)),
                "mentor");

        assertValidationProblem(result, "firstName", "phone");
        assertThat(result).bodyJson().isLenientlyEqualTo("""
                {"errors": [
                  {"field": "firstName", "message": "must be at most 100 characters"},
                  {"field": "phone", "message": "must be a valid phone number"}
                ]}
                """);
        assertThat(send(HttpMethod.GET, "/api/v1/me", "", "mentor"))
                .bodyJson()
                .extractingPath("$.user.lastName")
                .isEqualTo("Petrova");
        assertThat(users.count("audit_log")).isZero();
    }

    @Test
    @DisplayName("A text that is too long is rejected")
    void aTooLongTextIsRejectedNamingQ() {
        assertValidationProblem(send(HttpMethod.GET, "/api/v1/users?q=" + "x".repeat(101), "", "admin"), "q");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "{\"reason\": \"RETIRED\"}",
                "{\"reason\": \"OTHER\", \"comment\": \"   \"}",
                "{\"reason\": \"GRADUATED\", \"comment\": \"%s\"}"
            })
    @DisplayName("USR-4.2 An invalid disabling is rejected")
    void anInvalidDisablingIsRejectedAndChangesNothing(String body) {
        String field = body.contains("comment") ? "comment" : "reason";

        MvcTestResult result =
                send(HttpMethod.POST, "/api/v1/users/" + mentor + "/disable", body.formatted("c".repeat(501)), "admin");

        assertValidationProblem(result, field);
        assertThat(users.status(mentor)).isEqualTo("ACTIVE");
        assertThat(users.count("audit_log")).isZero();
    }

    @Test
    @DisplayName("USR-8.1 A profile is saved in its normal form")
    void aProfileIsSavedInItsNormalForm() {
        MvcTestResult result = send(
                HttpMethod.PUT,
                "/api/v1/users/" + mentor + "/profile",
                "{\"firstName\": \"  Ivan \", \"lastName\": \"    \", \"phone\": \"8 701 123 45 67\"}",
                "admin");

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.firstName").isEqualTo("Ivan");
        assertThat(result).bodyJson().extractingPath("$.lastName").isNull();
        assertThat(result).bodyJson().extractingPath("$.phone").isEqualTo("+77011234567");
        assertThat(result).bodyJson().extractingPath("$.email").isEqualTo("mentor@x.test");
    }

    @ParameterizedTest
    @ValueSource(strings = {"12", "+77011234567 ext. 12"})
    @DisplayName("Invalid values are rejected")
    void invalidProfileValuesAreRejectedAndNothingIsStored(String phone) {
        var before = jdbc.sql("select first_name, last_name, phone from users where id = :id")
                .param("id", mentor)
                .query()
                .singleRow();

        MvcTestResult result = send(
                HttpMethod.PUT,
                "/api/v1/users/" + mentor + "/profile",
                "{\"firstName\": \"%s\", \"lastName\": null, \"phone\": \"%s\"}".formatted("x".repeat(101), phone),
                "admin");

        assertValidationProblem(result, "firstName", "phone");
        assertThat(jdbc.sql("select first_name, last_name, phone from users where id = :id")
                        .param("id", mentor)
                        .query()
                        .singleRow())
                .isEqualTo(before);
        assertThat(users.count("audit_log")).isZero();
    }

    private MvcTestResult send(HttpMethod method, String uri, String body, String subject) {
        MockMvcRequestBuilder request = mvc.method(method)
                .uri(uri)
                .header(HttpHeaders.AUTHORIZATION, issuer.token(subject).bearer());
        if (!body.isEmpty()) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return request.exchange();
    }

    private static void assertValidationProblem(MvcTestResult result, String... fields) {
        assertProblem(result, HttpStatus.BAD_REQUEST, Problems.VALIDATION_FAILED);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[*].field")
                .asArray()
                .containsExactly((Object[]) fields);
    }
}
