package com.mentorhub.web;

/**
 * One invalid input of a {@link ValidationException}.
 *
 * @param field the query or path parameter name, or the request body property name
 */
public record FieldViolation(String field, String message) {}
