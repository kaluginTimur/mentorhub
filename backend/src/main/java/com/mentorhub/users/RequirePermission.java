package com.mentorhub.users;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * The operation requires the permission; without it the caller gets 403 {@code forbidden}. Every {@code /api/v1/**}
 * operation carries this annotation or {@link NoPermissionRequired}.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
// {value} is filled from the enum constant by the AnnotationTemplateExpressionDefaults bean in the security setup.
@PreAuthorize("hasAuthority('{value}')")
public @interface RequirePermission {

    Permission value();
}
