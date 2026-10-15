package com.mentorhub;

import static org.assertj.core.api.Assertions.assertThat;

import com.mentorhub.users.Role;
import com.mentorhub.users.internal.domain.UserStatus;
import com.mentorhub.users.internal.security.TestIssuer;
import com.mentorhub.users.internal.security.TestIssuerConfiguration;
import com.mentorhub.users.internal.security.TestUsers;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springdoc.webmvc.api.OpenApiWebMvcResource;
import org.springdoc.webmvc.ui.SwaggerWelcomeWebMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.web.PathMappedEndpoints;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** AC-25: without the dev profile only health is served, without details. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestIssuerConfiguration.class})
class ProductionProfileTests {

    private static final List<String> DEVELOPMENT_PATHS = List.of(
            "/swagger-ui.html",
            "/swagger-ui/index.html",
            "/v3/api-docs",
            "/v3/api-docs/swagger-config",
            "/openapi.yaml",
            "/actuator/info",
            "/actuator/modulith");

    @Autowired
    MockMvcTester mvc;

    @Autowired
    ApplicationContext context;

    @Autowired
    PathMappedEndpoints actuatorEndpoints;

    @Autowired
    TestIssuer issuer;

    @Autowired
    JdbcClient jdbc;

    @Test
    void apiDocumentationIsNotServed() {
        assertThat(context.getBeanNamesForType(OpenApiWebMvcResource.class)).isEmpty();
        assertThat(context.getBeanNamesForType(SwaggerWelcomeWebMvc.class)).isEmpty();
    }

    @Test
    void onlyHealthIsExposedAmongActuatorEndpoints() {
        assertThat(actuatorEndpoints.getAllPaths()).containsExactly("/actuator/health");
    }

    @Test
    void healthShowsNoDetails() {
        MvcTestResult result = mvc.get().uri("/actuator/health").exchange();

        assertThat(result).hasStatusOk();
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("UP");
        assertThat(result).bodyJson().doesNotHavePath("$.components").doesNotHavePath("$.details");
    }

    @Test
    void developmentPathsAreDeniedEvenToAnAdministrator() {
        var users = new TestUsers(jdbc);
        users.deleteAll();
        UUID admin = users.create("admin@example.com", UserStatus.ACTIVE, Role.ADMIN);
        users.link(admin, "admin");
        String token = issuer.token("admin").bearer();

        for (String path : DEVELOPMENT_PATHS) {
            assertThat(mvc.get().uri(path)).as(path).hasStatus(HttpStatus.UNAUTHORIZED);
            assertThat(mvc.get().uri(path).header(HttpHeaders.AUTHORIZATION, token))
                    .as(path)
                    .hasStatus(HttpStatus.FORBIDDEN);
        }
    }
}
