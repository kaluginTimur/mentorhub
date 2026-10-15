package com.mentorhub.users.internal.security;

import com.mentorhub.users.internal.domain.AccessStatus;
import com.mentorhub.web.ProblemResponseWriter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AnnotationTemplateExpressionDefaults;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Two filter chains: public endpoints without bearer token processing, so a stale token cannot fail them, and
 * everything else, where {@code /api/v1/**} requires a token and the gate, and any other path is denied.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
class SecurityConfiguration {

    private static final List<String> PUBLIC_PATHS =
            List.of("/api/v1/system/info", "/api/v1/system/client-config", "/actuator/health", "/actuator/health/**");

    private static final List<String> DEV_PATHS = List.of(
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/openapi.yaml",
            "/actuator/info",
            "/actuator/modulith");

    /** Lets {@code @RequirePermission} fill its {@code @PreAuthorize} template from the enum constant. */
    @Bean
    static AnnotationTemplateExpressionDefaults annotationTemplateExpressionDefaults() {
        return new AnnotationTemplateExpressionDefaults();
    }

    @Bean
    @Order(1)
    SecurityFilterChain publicEndpoints(HttpSecurity http, Environment environment) throws Exception {
        return http.securityMatcher(publicPaths(environment))
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain api(
            HttpSecurity http,
            AuthenticationManagerResolver<HttpServletRequest> authenticationManagerResolver,
            ProblemResponseWriter problems)
            throws Exception {
        var entryPoint = new ProblemAuthenticationEntryPoint(problems);
        var accessDeniedHandler = new ProblemAccessDeniedHandler(problems);
        return http.authorizeHttpRequests(requests -> requests
                        // Error dispatches render the error of a request that was already authorized.
                        .dispatcherTypeMatchers(DispatcherType.ERROR)
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/me")
                        .authenticated()
                        .requestMatchers("/api/v1/**")
                        .access(SecurityConfiguration::activeUser)
                        .anyRequest()
                        .denyAll())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .authenticationManagerResolver(authenticationManagerResolver)
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(exceptions ->
                        exceptions.authenticationEntryPoint(entryPoint).accessDeniedHandler(accessDeniedHandler))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }

    /** The gate: only an active user passes; others get 403 with their status from the access-denied handler. */
    private static AuthorizationResult activeUser(
            Supplier<? extends Authentication> authentication, RequestAuthorizationContext context) {
        return new AuthorizationDecision(authentication.get() instanceof MentorHubAuthentication caller
                && caller.status() == AccessStatus.ACTIVE);
    }

    private static RequestMatcher publicPaths(Environment environment) {
        List<String> paths = new ArrayList<>(PUBLIC_PATHS);
        if (environment.matchesProfiles("dev")) {
            paths.addAll(DEV_PATHS);
        }
        return new OrRequestMatcher(paths.stream()
                .<RequestMatcher>map(path -> PathPatternRequestMatcher.pathPattern(HttpMethod.GET, path))
                .toList());
    }
}
