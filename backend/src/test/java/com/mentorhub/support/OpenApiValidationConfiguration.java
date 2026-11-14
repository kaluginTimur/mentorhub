package com.mentorhub.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;

/**
 * Automatically validates every MockMvc response against {@code api/openapi.yaml}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class OpenApiValidationConfiguration {

    @Bean
    MockMvcBuilderCustomizer openApiValidationCustomizer() {
        return builder -> builder.alwaysExpect(OpenApiValidation.responseMatcher());
    }
}
