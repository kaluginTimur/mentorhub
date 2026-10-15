package com.mentorhub.users.internal.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

public final class ProblemAssertions {

    private ProblemAssertions() {}

    public static void assertProblem(MvcTestResult result, HttpStatus status, URI type) {
        assertThat(result).hasStatus(status).hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(result).bodyJson().extractingPath("$.type").isEqualTo(type.toString());
        assertThat(result).bodyJson().extractingPath("$.status").isEqualTo(status.value());
        assertThat(result).bodyJson().extractingPath("$.title").isNotNull();
    }
}
