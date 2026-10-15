package com.mentorhub.users.internal.security;

import com.mentorhub.web.ProblemResponseWriter;
import com.mentorhub.web.Problems;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;

/** 401 {@code unauthorized}, keeping the {@code WWW-Authenticate: Bearer} header of the standard entry point. */
final class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final AuthenticationEntryPoint bearer = new BearerTokenAuthenticationEntryPoint();
    private final ProblemResponseWriter writer;

    ProblemAuthenticationEntryPoint(ProblemResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException, ServletException {
        bearer.commence(request, response, exception);
        // The standard entry point answers a malformed request (RFC 6750 invalid_request) with 400.
        HttpStatus status = HttpStatus.valueOf(response.getStatus());
        ProblemDetail problem = status == HttpStatus.UNAUTHORIZED
                ? Problems.create(status, Problems.UNAUTHORIZED, "Unauthorized", "A valid access token is required.")
                : ProblemDetail.forStatus(status);
        writer.write(request, response, problem);
    }
}
