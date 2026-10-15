package com.mentorhub.users.internal.dev;

import com.mentorhub.users.internal.persistence.UserRepository;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
@Profile("dev")
@EnableConfigurationProperties(DevelopmentUsersProperties.class)
class DevelopmentUsersConfiguration {

    @Bean
    DevelopmentUserRunner developmentUserRunner(
            DevelopmentUsersProperties properties, UserRepository users, TransactionTemplate transactions) {
        return new DevelopmentUserRunner(properties, users, transactions);
    }
}
