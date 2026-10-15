package com.mentorhub.users.internal.security;

import com.mentorhub.users.internal.domain.AccessStatus;
import com.mentorhub.web.ProblemResponseWriter;
import com.mentorhub.web.Problems;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * 403 for an authenticated caller: {@code not-invited} and {@code access-ended} from the gate, {@code forbidden} for
 * an active user without the permission.
 */
final class ProblemAccessDeniedHandler implements AccessDeniedHandler {

    private final ProblemResponseWriter writer;

    ProblemAccessDeniedHandler(ProblemResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException exception)
            throws IOException {
        Authentication authentication =
                SecurityContextHolder.getContextHolderStrategy().getContext().getAuthentication();
        AccessStatus status =
                authentication instanceof MentorHubAuthentication caller ? caller.status() : AccessStatus.ACTIVE;
        writer.write(request, response, problem(status));
    }

    private static ProblemDetail problem(AccessStatus status) {
        return switch (status) {
            case NOT_INVITED ->
                Problems.create(
                        HttpStatus.FORBIDDEN,
                        Problems.NOT_INVITED,
                        "Not invited",
                        "Access to MentorHub is by invitation only.");
            case DISABLED ->
                Problems.create(
                        HttpStatus.FORBIDDEN,
                        Problems.ACCESS_ENDED,
                        "Access ended",
                        "Your access to MentorHub has ended.");
            case ACTIVE ->
                Problems.create(
                        HttpStatus.FORBIDDEN, Problems.FORBIDDEN, "Forbidden", "You don't have permission to do this.");
        };
    }
}
