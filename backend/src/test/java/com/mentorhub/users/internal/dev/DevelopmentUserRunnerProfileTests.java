package com.mentorhub.users.internal.dev;

import static org.assertj.core.api.Assertions.assertThat;

import com.mentorhub.TestcontainersConfiguration;
import com.mentorhub.users.internal.security.TestIssuerConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;

/** Without the dev profile there is no development user runner. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestIssuerConfiguration.class})
class DevelopmentUserRunnerProfileTests {

    @Autowired
    ApplicationContext context;

    @Test
    void runnerExistsOnlyInTheDevProfile() {
        assertThat(context.getBeanNamesForType(DevelopmentUserRunner.class)).isEmpty();
        assertThat(context.getBeanNamesForType(DevelopmentUsersProperties.class))
                .isEmpty();
    }
}
