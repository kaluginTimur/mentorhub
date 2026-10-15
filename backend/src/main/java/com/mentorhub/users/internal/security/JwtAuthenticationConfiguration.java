package com.mentorhub.users.internal.security;

import com.mentorhub.users.internal.bootstrap.AdministratorBootstrap;
import com.mentorhub.users.internal.persistence.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.oauth2.server.resource.authentication.JwtIssuerAuthenticationManagerResolver;
import org.springframework.transaction.support.TransactionTemplate;

/** Token validation per trusted issuer and resolution of the caller. Kept apart from the filter chains for tests. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SecurityProperties.class)
class JwtAuthenticationConfiguration {

    @Bean
    AuthenticationManagerResolver<HttpServletRequest> jwtAuthenticationManagerResolver(
            SecurityProperties properties, UserAuthenticationResolver users) {
        return new JwtIssuerAuthenticationManagerResolver(
                new TrustedIssuers(properties.trustedIssuers(), users::resolve));
    }

    @Bean
    AdministratorBootstrap administratorBootstrap(
            UserRepository users, TransactionTemplate transactions, SecurityProperties properties) {
        return new AdministratorBootstrap(users, transactions, properties.bootstrapAdminEmail());
    }
}
