package com.mentorhub.users.internal.security;

import com.mentorhub.web.ProblemResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.authentication.BadCredentialsException;

/**
 * The application's filter chains, gate, method security and Problem Details for {@code @WebMvcTest} slices. Bearer
 * tokens are rejected; authenticate requests with {@link TestAuthentications}.
 */
@TestConfiguration(proxyBeanMethods = false)
@Import({SecurityConfiguration.class, ProblemResponseWriter.class, SecurityContextCurrentUserProvider.class})
public class WebSliceSecurity {

    @Bean
    AuthenticationManagerResolver<HttpServletRequest> rejectingAuthenticationManagerResolver() {
        return request -> authentication -> {
            throw new BadCredentialsException("Web slice tests authenticate with TestAuthentications");
        };
    }
}
