package com.mentorhub.users.internal.security;

import static com.mentorhub.users.internal.security.ProblemAssertions.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;

import com.mentorhub.users.Permission;
import com.mentorhub.web.Problems;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** The slice setup other modules' controller tests use. */
@WebMvcTest(PermissionProbeController.class)
@Import(WebSliceSecurity.class)
class WebSliceSecurityTests {

    @Autowired
    MockMvcTester mvc;

    @Test
    void anonymousRequestIsUnauthorized() {
        assertProblem(
                mvc.get().uri("/api/v1/test/current-user").exchange(), HttpStatus.UNAUTHORIZED, Problems.UNAUTHORIZED);
    }

    @Test
    void gateRefusesNotInvitedAndDisabledCallers() {
        assertProblem(
                mvc.get()
                        .uri("/api/v1/test/current-user")
                        .with(TestAuthentications.notInvited())
                        .exchange(),
                HttpStatus.FORBIDDEN,
                Problems.NOT_INVITED);
        assertProblem(
                mvc.get()
                        .uri("/api/v1/test/current-user")
                        .with(TestAuthentications.disabledUser(UUID.randomUUID()))
                        .exchange(),
                HttpStatus.FORBIDDEN,
                Problems.ACCESS_ENDED);
    }

    @Test
    void permissionsComeFromTheTestAuthentication() {
        UUID userId = UUID.randomUUID();

        assertProblem(
                mvc.get()
                        .uri("/api/v1/test/users-read")
                        .with(TestAuthentications.activeUser(userId))
                        .exchange(),
                HttpStatus.FORBIDDEN,
                Problems.FORBIDDEN);
        assertThat(mvc.get()
                        .uri("/api/v1/test/users-read")
                        .with(TestAuthentications.activeUser(userId, Permission.USERS_READ)))
                .hasStatusOk();
        assertThat(mvc.get()
                        .uri("/api/v1/test/current-user")
                        .with(TestAuthentications.activeUser(userId, Permission.USERS_READ)))
                .bodyJson()
                .extractingPath("$.id")
                .isEqualTo(userId.toString());
    }
}
