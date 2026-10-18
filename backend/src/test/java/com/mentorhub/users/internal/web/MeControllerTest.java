package com.mentorhub.users.internal.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.mentorhub.users.Permission;
import com.mentorhub.users.Role;
import com.mentorhub.users.internal.domain.AccessStatus;
import com.mentorhub.users.internal.security.TestAuthentications;
import com.mentorhub.users.internal.security.WebSliceSecurity;
import com.mentorhub.users.internal.service.MeDetails;
import com.mentorhub.users.internal.service.MeService;
import com.mentorhub.users.internal.service.UserProfile;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@WebMvcTest(MeController.class)
@Import(WebSliceSecurity.class)
class MeControllerTest {

    private static final UUID USER_ID = UUID.fromString("0b6f1d2e-6a51-4f36-9b0c-5f2d8f3a1c10");

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    MeService meService;

    // AC-3
    @Test
    void notInvitedCallerGetsOnlyItsStatus() {
        given(meService.me()).willReturn(new MeDetails(AccessStatus.NOT_INVITED, null));

        MvcTestResult result = mvc.get()
                .uri("/api/v1/me")
                .with(TestAuthentications.notInvited())
                .exchange();

        assertThat(result).hasStatusOk().bodyJson().isStrictlyEqualTo("{\"status\": \"NOT_INVITED\"}");
    }

    // AC-4
    @Test
    void disabledUserGetsOnlyItsStatus() {
        given(meService.me()).willReturn(new MeDetails(AccessStatus.DISABLED, null));

        MvcTestResult result = mvc.get()
                .uri("/api/v1/me")
                .with(TestAuthentications.disabledUser(USER_ID))
                .exchange();

        assertThat(result).hasStatusOk().bodyJson().isStrictlyEqualTo("{\"status\": \"DISABLED\"}");
    }

    // AC-7
    @Test
    void activeUserWithoutRolesHasEmptyRolesAndPermissions() {
        given(meService.me())
                .willReturn(new MeDetails(
                        AccessStatus.ACTIVE,
                        new UserProfile(USER_ID, "anna@example.com", "Anna", "Petrova", null, List.of(), List.of())));

        MvcTestResult result = mvc.get()
                .uri("/api/v1/me")
                .with(TestAuthentications.activeUser(USER_ID))
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("ACTIVE");
        assertThat(result).bodyJson().extractingPath("$.user.roles").asArray().isEmpty();
        assertThat(result)
                .bodyJson()
                .extractingPath("$.user.permissions")
                .asArray()
                .isEmpty();
    }

    // AC-9
    @Test
    void activeUserGetsProfileRolesAndPermissions() {
        given(meService.me())
                .willReturn(new MeDetails(
                        AccessStatus.ACTIVE,
                        new UserProfile(
                                USER_ID,
                                "anna@example.com",
                                "Anna",
                                "Petrova",
                                null,
                                List.of(Role.ADMIN, Role.MENTOR),
                                List.of(Permission.USERS_READ, Permission.USERS_MANAGE))));

        MvcTestResult result = mvc.get()
                .uri("/api/v1/me")
                .with(TestAuthentications.activeUser(USER_ID, Permission.USERS_READ, Permission.USERS_MANAGE))
                .exchange();

        assertThat(result).hasStatusOk().bodyJson().isStrictlyEqualTo("""
                {
                  "status": "ACTIVE",
                  "user": {
                    "id": "0b6f1d2e-6a51-4f36-9b0c-5f2d8f3a1c10",
                    "email": "anna@example.com",
                    "firstName": "Anna",
                    "lastName": "Petrova",
                    "phone": null,
                    "roles": ["ADMIN", "MENTOR"],
                    "permissions": ["USERS_READ", "USERS_MANAGE"]
                  }
                }
                """);
    }

    @Test
    void missingNamesAreSentAsNull() {
        given(meService.me())
                .willReturn(new MeDetails(
                        AccessStatus.ACTIVE,
                        new UserProfile(USER_ID, "anna@example.com", null, null, null, List.of(), List.of())));

        MvcTestResult result = mvc.get()
                .uri("/api/v1/me")
                .with(TestAuthentications.activeUser(USER_ID))
                .exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().hasPath("$.user.firstName").hasPath("$.user.lastName");
        assertThat(result).bodyJson().extractingPath("$.user.firstName").isNull();
        assertThat(result).bodyJson().extractingPath("$.user.lastName").isNull();
    }
}
