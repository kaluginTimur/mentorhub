package com.mentorhub.users.internal.security;

import com.mentorhub.users.Permission;
import com.mentorhub.users.RequirePermission;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Checks the {@link RequirePermission} of the operation once its handler is known and before any argument is resolved,
 * so a caller without the permission gets 403 before the body is read, converted or validated (PLT-10). The
 * {@link AccessDeniedException} reaches Spring Security's access-denied handler like any other.
 */
final class PermissionInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod method) {
            RequirePermission required = requiredPermission(method);
            if (required != null && !holdsAny(required.value())) {
                throw new AccessDeniedException("The caller holds none of " + Arrays.toString(required.value()));
            }
        }
        return true;
    }

    private static @Nullable RequirePermission requiredPermission(HandlerMethod method) {
        RequirePermission onMethod = method.getMethodAnnotation(RequirePermission.class);
        return onMethod != null
                ? onMethod
                : AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), RequirePermission.class);
    }

    private static boolean holdsAny(Permission[] permissions) {
        Authentication authentication =
                SecurityContextHolder.getContextHolderStrategy().getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        Set<String> held = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        return Arrays.stream(permissions).map(Permission::name).anyMatch(held::contains);
    }
}
