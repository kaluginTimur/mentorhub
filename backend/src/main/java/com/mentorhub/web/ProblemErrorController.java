package com.mentorhub.web;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Renders errors that never reach Spring MVC's exception handlers as Problem Details, replacing Boot's
 * {@code BasicErrorController}: exceptions from servlet filters (for example the security filter chain when an
 * issuer's keys cannot be loaded, or the database fails while resolving the caller) and container errors. Reachable
 * only through an error dispatch; a direct request to the error path is denied by security.
 */
@RestController
class ProblemErrorController implements ErrorController {

    @RequestMapping("${spring.web.error.path:${error.path:/error}}")
    ResponseEntity<ProblemDetail> error(HttpServletRequest request) {
        HttpStatusCode status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) instanceof Integer code
                ? HttpStatusCode.valueOf(code)
                : HttpStatus.INTERNAL_SERVER_ERROR;
        ProblemDetail problem = Problems.withDefaultType(ProblemDetail.forStatus(status));
        if (request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI) instanceof String path) {
            problem.setInstance(URI.create(path));
        }
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
