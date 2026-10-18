package com.mentorhub.users.internal.web;

import static com.mentorhub.users.internal.security.ProblemAssertions.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;

import com.mentorhub.TestcontainersConfiguration;
import com.mentorhub.users.Role;
import com.mentorhub.users.internal.domain.UserStatus;
import com.mentorhub.users.internal.security.TestIssuer;
import com.mentorhub.users.internal.security.TestIssuerConfiguration;
import com.mentorhub.users.internal.security.TestUsers;
import com.mentorhub.users.internal.service.LockoutProblems;
import com.mentorhub.web.Problems;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
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
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestIssuerConfiguration.class})
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

    /** Every non-admin role and no roles at all, against every {@code /api/v1/users/**} operation. */
    static Stream<Arguments> forbiddenCalls() {
        List<@Nullable Role> callerRoles = new ArrayList<>();
        callerRoles.add(null);
        Stream.of(Role.values()).filter(role -> role != Role.ADMIN).forEach(callerRoles::add);
        List<Arguments> calls = new ArrayList<>();
        for (Role role : callerRoles) {
            for (String[] operation : List.of(
                    new String[] {"GET", "/api/v1/users", ""},
                    new String[] {"GET", "/api/v1/users/{id}", ""},
                    new String[] {"GET", "/api/v1/users/{id}/history", ""},
                    new String[] {"POST", "/api/v1/users/{id}/disable", "{\"reason\": \"GRADUATED\"}"},
                    new String[] {"POST", "/api/v1/users/{id}/enable", ""},
                    new String[] {"PUT", "/api/v1/users/{id}/roles", "{\"roles\": [\"ADMIN\"]}"},
                    new String[] {"PUT", "/api/v1/users/{id}/profile", PROFILE_BODY})) {
                calls.add(Arguments.of(
                        role == null ? "no roles" : role.name(), operation[0], operation[1], operation[2]));
            }
        }
        return calls.stream();
    }

    // AC-5: permissions come from the stored roles; only ADMIN has them. Nothing changes and nothing is audited.
    @ParameterizedTest(name = "{0}: {1} {2}")
    @MethodSource("forbiddenCalls")
    void callersWithoutTheUsersPermissionsAreForbidden(String role, String method, String path, String body) {
        UUID caller = role.equals("no roles")
                ? users.create("caller@x.test", UserStatus.ACTIVE)
                : users.create("caller@x.test", UserStatus.ACTIVE, Role.valueOf(role));
        users.link(caller, "caller");

        MvcTestResult result =
                send(HttpMethod.valueOf(method), path.replace("{id}", mentor.toString()), body, "caller");

        assertProblem(result, HttpStatus.FORBIDDEN, Problems.FORBIDDEN);
        assertThat(users.status(mentor)).isEqualTo("ACTIVE");
        assertThat(users.roles(mentor)).containsExactly("MENTOR");
        assertThat(users.count("audit_log")).isZero();
    }

    // AC-3 through the full application: method validation, conversion and service validation.
    @Test
    void invalidParametersAreValidationProblemsNamingTheParameter() {
        assertValidationProblem(send(HttpMethod.GET, "/api/v1/users?size=101", "", "admin"), "size");
        assertValidationProblem(send(HttpMethod.GET, "/api/v1/users?page=-1", "", "admin"), "page");
        assertValidationProblem(send(HttpMethod.GET, "/api/v1/users?role=SUPERUSER", "", "admin"), "role");
        assertValidationProblem(send(HttpMethod.GET, "/api/v1/users?status=GONE", "", "admin"), "status");
        assertValidationProblem(send(HttpMethod.GET, "/api/v1/users?q=" + "x".repeat(101), "", "admin"), "q");
        assertValidationProblem(send(HttpMethod.GET, "/api/v1/users/not-a-uuid", "", "admin"), "id");
        assertValidationProblem(
                send(HttpMethod.GET, "/api/v1/users/" + mentor + "/history?size=0", "", "admin"), "size");
        assertValidationProblem(
                send(HttpMethod.POST, "/api/v1/users/" + mentor + "/disable", "{\"reason\": \"RETIRED\"}", "admin"),
                "reason");
    }

    // AC-1, AC-2 through the full application.
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

    // AC-4
    @Test
    void unknownUserIsNotFound() {
        assertProblem(
                send(HttpMethod.GET, "/api/v1/users/" + UUID.randomUUID(), "", "admin"),
                HttpStatus.NOT_FOUND,
                Problems.NOT_FOUND);
    }

    // AC-6, AC-8, AC-19
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

    // AC-10, AC-11: role changes take effect on the user's next request.
    @Test
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

    // AC-13: the API answers lockout with 409 and changes nothing.
    @Test
    void administratorCannotLockThemselvesOut() {
        MvcTestResult disable =
                send(HttpMethod.POST, "/api/v1/users/" + admin + "/disable", "{\"reason\": \"SECURITY\"}", "admin");
        MvcTestResult roles =
                send(HttpMethod.PUT, "/api/v1/users/" + admin + "/roles", "{\"roles\": [\"MENTOR\"]}", "admin");

        assertProblem(disable, HttpStatus.CONFLICT, LockoutProblems.SELF_LOCKOUT);
        assertThat(disable).bodyJson().extractingPath("$.detail").isEqualTo("You can't lock yourself out.");
        assertProblem(roles, HttpStatus.CONFLICT, LockoutProblems.SELF_LOCKOUT);
        assertThat(users.status(admin)).isEqualTo("ACTIVE");
        assertThat(users.roles(admin)).containsExactly("ADMIN");
        assertThat(users.count("audit_log")).isZero();
    }

    // AC-16, AC-18
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

    // AC-17: a user without roles changes their own profile; GET /me returns the new values.
    @Test
    void userWithoutRolesUpdatesTheirOwnProfile() {
        UUID maria = users.create("maria@x.test", UserStatus.ACTIVE);
        users.link(maria, "maria");

        MvcTestResult result = send(HttpMethod.PUT, "/api/v1/me/profile", PROFILE_BODY, "maria");

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.user.phone").isEqualTo("+77011234567");
        MvcTestResult me = send(HttpMethod.GET, "/api/v1/me", "", "maria");
        assertThat(me).bodyJson().extractingPath("$.user.firstName").isEqualTo("Ivan");
        assertThat(me).bodyJson().extractingPath("$.user.lastName").isEqualTo("Sidorov");
        assertThat(me).bodyJson().extractingPath("$.user.phone").isEqualTo("+77011234567");
        assertThat(users.count("audit_log")).isEqualTo(1);
    }

    // AC-18
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
