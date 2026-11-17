package com.mentorhub.users;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The operation requires one of the permissions; without any of them the caller gets 403 {@code forbidden}. Every
 * {@code /api/v1/**} operation carries this annotation or {@link NoPermissionRequired}. An operation that allows more
 * than one permission, one of them only together with a relation to the resource, checks the relation in its service.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequirePermission {

    Permission[] value();
}
