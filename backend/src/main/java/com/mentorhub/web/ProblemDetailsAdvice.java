package com.mentorhub.web;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.exc.ValueInstantiationException;

@RestControllerAdvice
class ProblemDetailsAdvice extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailsAdvice.class);

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound() {
        return Problems.create(HttpStatus.NOT_FOUND, Problems.NOT_FOUND, "Not Found", null);
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail conflict(ConflictException exception) {
        return Problems.create(HttpStatus.CONFLICT, exception.type(), "Conflict", exception.detail());
    }

    @ExceptionHandler(ValidationException.class)
    ProblemDetail validationFailed(ValidationException exception) {
        return Problems.validationFailed(exception.errors());
    }

    /**
     * {@code @Min}/{@code @Max} on parameters of a {@code @Validated} controller interface, checked by the method
     * validation proxy. The violation's last path node is the Java parameter name, which controllers keep equal to the
     * request parameter name.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail constraintViolation(ConstraintViolationException exception) {
        return Problems.validationFailed(exception.getConstraintViolations().stream()
                .map(violation -> new FieldViolation(lastNodeName(violation), violation.getMessage()))
                .sorted(Comparator.comparing(FieldViolation::field))
                .toList());
    }

    // Security exceptions must reach Spring Security's ExceptionTranslationFilter; the catch-all below would turn
    // method security's AccessDeniedException into a 500.
    @ExceptionHandler({AccessDeniedException.class, AuthenticationException.class})
    void rethrowSecurityException(RuntimeException exception) {
        throw exception;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception exception) {
        log.error("Unhandled exception", exception);
        return Problems.withDefaultType(ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR));
    }

    /** {@code @Valid @RequestBody}: one error per invalid property. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldViolation> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), message(error)))
                .sorted(Comparator.comparing(FieldViolation::field))
                .toList();
        if (errors.isEmpty()) {
            return super.handleMethodArgumentNotValid(exception, headers, status, request);
        }
        return validationResponse(exception, errors, headers, request);
    }

    /** A path or query parameter that does not convert, such as a non-UUID {@code id} or an unknown enum value. */
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (!(exception instanceof MethodArgumentTypeMismatchException mismatch)) {
            return super.handleTypeMismatch(exception, headers, status, request);
        }
        FieldViolation error = new FieldViolation(mismatch.getName(), invalidValueMessage(mismatch.getRequiredType()));
        return validationResponse(exception, List.of(error), headers, request);
    }

    /**
     * A body property Jackson cannot bind, such as an unknown enum value, is a validation error of that property. A body
     * that is not JSON at all, or has no property to blame, keeps Spring's generic 400.
     */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (exception.getCause() instanceof DatabindException cause) {
            String field = propertyPath(cause);
            if (!field.isEmpty()) {
                FieldViolation error = new FieldViolation(field, invalidValueMessage(targetType(cause)));
                return validationResponse(exception, List.of(error), headers, request);
            }
        }
        return super.handleHttpMessageNotReadable(exception, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> createResponseEntity(
            @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problem) {
            Problems.withDefaultType(problem);
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    private ResponseEntity<Object> validationResponse(
            Exception exception, List<FieldViolation> errors, HttpHeaders headers, WebRequest request) {
        ResponseEntity<Object> response = handleExceptionInternal(
                exception, Problems.validationFailed(errors), headers, HttpStatus.BAD_REQUEST, request);
        return Objects.requireNonNull(response);
    }

    private static String message(FieldError error) {
        String message = error.getDefaultMessage();
        return message != null ? message : "is invalid";
    }

    private static String invalidValueMessage(@Nullable Class<?> type) {
        if (type != null && type.isEnum()) {
            return Arrays.stream(type.getEnumConstants())
                    .map(Object::toString)
                    .collect(Collectors.joining(", ", "must be one of ", ""));
        }
        if (type == UUID.class) {
            return "must be a UUID";
        }
        return "has an invalid value";
    }

    private static String lastNodeName(ConstraintViolation<?> violation) {
        String name = null;
        for (Path.Node node : violation.getPropertyPath()) {
            name = node.getName();
        }
        return name != null ? name : violation.getPropertyPath().toString();
    }

    /** The property names of the path, without array indexes: {@code roles} for {@code roles[0]}. */
    private static String propertyPath(JacksonException exception) {
        return exception.getPath().stream()
                .map(JacksonException.Reference::getPropertyName)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("."));
    }

    private static @Nullable Class<?> targetType(DatabindException exception) {
        if (exception instanceof MismatchedInputException mismatch) {
            return mismatch.getTargetType();
        }
        if (exception instanceof ValueInstantiationException instantiation) {
            return instantiation.getType().getRawClass();
        }
        return null;
    }
}
