package com.mentorhub.users;

import org.springframework.security.core.annotation.ExpressionTemplateValueProvider;

/** A capability that code checks (decision 0004). Must equal the {@code Permission} enum of the API contract. */
public enum Permission implements ExpressionTemplateValueProvider {
    USERS_READ,
    USERS_MANAGE,
    USERS_INVITE,
    TRACKS_READ,
    TRACKS_MANAGE,
    ENROLLMENTS_READ,
    ENROLLMENTS_MANAGE,
    ENROLLMENTS_MANAGE_COORDINATED;

    /**
     * The constant as a SpEL string literal, for the {@code @PreAuthorize} template of {@link RequirePermission}: the
     * template engine joins an array attribute with commas and adds no quotes itself.
     */
    @Override
    public String getExpressionTemplateValue() {
        return "'" + name() + "'";
    }
}
