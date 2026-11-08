package com.mentorhub.users.internal.security;

import static com.mentorhub.users.internal.security.ProblemAssertions.assertProblem;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import com.mentorhub.users.Permission;
import com.mentorhub.web.NotFoundException;
import com.mentorhub.web.Problems;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** The slice setup other modules' controller tests use. */
@WebMvcTest(PermissionProbeController.class)
@Import(WebSliceSecurity.class)
class WebSliceSecurityTests {

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    ProbeResources resources;

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

    @Test
    void holderOfThePermissionChangesTheResource() {
        UUID id = UUID.randomUUID();

        assertThat(change(id, Permission.USERS_MANAGE)).hasStatusOk();
        then(resources).should().change(id);
    }

    @Test
    @DisplayName("PERM-3.2 A caller without the permission is refused")
    void callerWithoutThePermissionIsRefusedAndNothingChanges() {
        MvcTestResult result = change(UUID.randomUUID(), Permission.USERS_READ, Permission.USERS_INVITE);

        assertProblem(result, HttpStatus.FORBIDDEN, Problems.FORBIDDEN);
        then(resources).shouldHaveNoInteractions();
    }

    // Without the permission check first, the lookup would answer 404 and tell the caller the id does not exist.
    @Test
    @DisplayName("PERM-6.1 The permission is checked before the resource is looked up")
    void permissionIsCheckedBeforeAMissingResourceIsLookedUp() {
        willThrow(new NotFoundException()).given(resources).change(any());

        MvcTestResult result = change(UUID.randomUUID(), Permission.USERS_READ);

        assertProblem(result, HttpStatus.FORBIDDEN, Problems.FORBIDDEN);
        then(resources).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("PERM-6.2 A missing resource is not found")
    void missingResourceIsNotFoundForAHolderOfThePermission() {
        willThrow(new NotFoundException()).given(resources).change(any());

        assertProblem(change(UUID.randomUUID(), Permission.USERS_MANAGE), HttpStatus.NOT_FOUND, Problems.NOT_FOUND);
    }

    @Test
    @DisplayName("PLT-2.2 A path id that is not a UUID is named")
    void pathIdThatIsNotAUuidIsNamed() {
        MvcTestResult result = change("not-a-uuid", Permission.USERS_MANAGE);

        assertProblem(result, HttpStatus.BAD_REQUEST, Problems.VALIDATION_FAILED);
        assertThat(result)
                .bodyJson()
                .extractingPath("$.errors[*].field")
                .asArray()
                .containsExactly("id");
        assertThat(result).bodyJson().extractingPath("$.errors[0].message").isEqualTo("must be a UUID");
        then(resources).shouldHaveNoInteractions();
    }

    private MvcTestResult change(Object id, Permission... permissions) {
        return mvc.post()
                .uri("/api/v1/test/managed-resources/{id}", id)
                .with(TestAuthentications.activeUser(UUID.randomUUID(), permissions))
                .exchange();
    }
}
