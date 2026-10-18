package com.mentorhub.web;

import java.util.List;
import java.util.stream.Collectors;

/** Invalid input found by service validation, with one violation per invalid field: 400 {@code validation-failed}. */
public class ValidationException extends RuntimeException {

    private final List<FieldViolation> errors;

    public ValidationException(List<FieldViolation> errors) {
        super(errors.stream()
                .map(error -> error.field() + ": " + error.message())
                .collect(Collectors.joining("; ", "Validation failed: ", "")));
        if (errors.isEmpty()) {
            throw new IllegalArgumentException("A validation failure needs at least one error");
        }
        this.errors = List.copyOf(errors);
    }

    public static ValidationException of(String field, String message) {
        return new ValidationException(List.of(new FieldViolation(field, message)));
    }

    public List<FieldViolation> errors() {
        return errors;
    }
}
