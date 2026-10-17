package com.mentorhub.users.internal.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.mentorhub.TestcontainersConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Errors inside the security filter chain reach the servlet container's error dispatch, which MockMvc does not
 * perform, so this test runs a real server.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, TestIssuerConfiguration.class})
class IssuerFailureTests {

    @LocalServerPort
    int port;

    @Autowired
    TestIssuer issuer;

    @Autowired
    JsonMapper jsonMapper;

    @Test
    void unavailableIssuerKeysAnswerAProblemWithoutInternals() throws Exception {
        HttpResponse<String> response = getMe(TestIssuer.UNREACHABLE_ISSUER);

        assertServerErrorProblem(response);
        assertThat(response.body())
                .doesNotContain("127.0.0.1")
                .doesNotContain("JWK")
                .doesNotContain("trace");
    }

    @Test
    void hangingIssuerFailsWithinTheReadTimeout() throws Exception {
        long started = System.nanoTime();

        HttpResponse<String> response = getMe(TestIssuer.HANGING_ISSUER);

        // The test configuration sets a 1 s read timeout; without one the request would hang.
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(5));
        assertServerErrorProblem(response);
    }

    @Test
    void theErrorPathCannotBeRequestedDirectly() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/error"))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(401);
    }

    private HttpResponse<String> getMe(String tokenIssuer) throws Exception {
        String token = issuer.token("anna").claim("iss", tokenIssuer).bearer();
        return HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/me"))
                                .header(HttpHeaders.AUTHORIZATION, token)
                                .timeout(Duration.ofSeconds(30))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
    }

    private void assertServerErrorProblem(HttpResponse<String> response) {
        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.headers().firstValue(HttpHeaders.CONTENT_TYPE))
                .hasValueSatisfying(type ->
                        assertThat(MediaType.parseMediaType(type)).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON));
        JsonNode problem = jsonMapper.readTree(response.body());
        assertThat(problem.path("type").asString()).isEqualTo("about:blank");
        assertThat(problem.path("title").asString()).isEqualTo("Internal Server Error");
        assertThat(problem.path("status").asInt()).isEqualTo(500);
        assertThat(problem.path("instance").asString()).isEqualTo("/api/v1/me");
    }
}
