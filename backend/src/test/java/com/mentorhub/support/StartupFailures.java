package com.mentorhub.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.test.system.CapturedOutput;

/** Starts a minimal application without any configuration files and checks Spring Boot's failure report. */
public final class StartupFailures {

    private StartupFailures() {}

    public static void assertStartupFailsNaming(CapturedOutput output, Class<?> configuration, String... properties) {
        SpringApplication application = new SpringApplication(configuration);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setBannerMode(Banner.Mode.OFF);

        assertThatThrownBy(() -> application.run("--spring.config.location=optional:classpath:/no-such-config/"))
                .isInstanceOf(RuntimeException.class);
        assertThat(output.getOut()).contains("APPLICATION FAILED TO START").contains(properties);
    }
}
