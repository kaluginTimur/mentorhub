package com.mentorhub.web;

import java.net.URI;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/** RFC 9457 problem types of the API contract. Errors without a MentorHub type keep {@code about:blank}. */
public final class Problems {

    public static final URI UNAUTHORIZED = URI.create("urn:mentorhub:problem:unauthorized");
    public static final URI NOT_INVITED = URI.create("urn:mentorhub:problem:not-invited");
    public static final URI ACCESS_ENDED = URI.create("urn:mentorhub:problem:access-ended");
    public static final URI FORBIDDEN = URI.create("urn:mentorhub:problem:forbidden");
    public static final URI NOT_FOUND = URI.create("urn:mentorhub:problem:not-found");
    public static final URI VALIDATION_FAILED = URI.create("urn:mentorhub:problem:validation-failed");
    public static final URI ABOUT_BLANK = URI.create("about:blank");

    private Problems() {}

    public static ProblemDetail create(HttpStatus status, URI type, String title, @Nullable String detail) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setType(type);
        problem.setTitle(title);
        if (detail != null) {
            problem.setDetail(detail);
        }
        return problem;
    }

    /**
     * Sets {@code about:blank} when the problem has no type. Spring leaves the type {@code null} and omits it from the
     * JSON, but the contract requires it.
     */
    public static ProblemDetail withDefaultType(ProblemDetail problem) {
        if (problem.getType() == null) {
            problem.setType(ABOUT_BLANK);
        }
        return problem;
    }
}
