package com.mentorhub.users.internal.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.mentorhub.TestcontainersConfiguration;
import com.mentorhub.users.NoPermissionRequired;
import com.mentorhub.users.RequirePermission;
import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** AC-18: every {@code /api/v1/**} operation declares a permission or that it needs none, and exactly one of them. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestIssuerConfiguration.class})
class PermissionDeclarationTests {

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping handlerMapping;

    @Test
    void everyApiOperationDeclaresExactlyOnePermissionRule() {
        List<Map.Entry<RequestMappingInfo, HandlerMethod>> apiOperations =
                handlerMapping.getHandlerMethods().entrySet().stream()
                        .filter(entry -> entry.getKey().getPatternValues().stream()
                                .anyMatch(pattern -> pattern.startsWith("/api/v1/")))
                        .toList();

        assertThat(apiOperations)
                .extracting(entry -> entry.getValue().getMethod().getName())
                .contains("getMe", "getSystemInfo", "getClientConfig");
        assertThat(apiOperations)
                .filteredOn(entry -> declarations(entry.getValue()) != 1)
                .extracting(entry -> entry.getValue().getShortLogMessage())
                .as("operations without exactly one of @RequirePermission and @NoPermissionRequired")
                .isEmpty();
    }

    private static int declarations(HandlerMethod handler) {
        return (declares(handler, RequirePermission.class) ? 1 : 0)
                + (declares(handler, NoPermissionRequired.class) ? 1 : 0);
    }

    private static boolean declares(HandlerMethod handler, Class<? extends Annotation> annotation) {
        return AnnotatedElementUtils.hasAnnotation(handler.getMethod(), annotation)
                || AnnotatedElementUtils.hasAnnotation(handler.getBeanType(), annotation);
    }
}
