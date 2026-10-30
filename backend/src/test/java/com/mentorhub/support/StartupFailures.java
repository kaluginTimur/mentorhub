package com.mentorhub.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.test.system.CapturedOutput;

/** Starts a minimal application without any configuration files and checks Spring Boot's failure report. */
public final class StartupFailures {

    private StartupFailures() {}

    public static void assertStartupFailsNaming(CapturedOutput output, Class<?> configuration, String... properties) {
        assertStartupFailsNaming(output, configuration, List.of(), properties);
    }

    /** @param arguments command line arguments such as {@code --spring.mail.host=127.0.0.1} */
    public static void assertStartupFailsNaming(
            CapturedOutput output, Class<?> configuration, List<String> arguments, String... properties) {
        SpringApplication application = new SpringApplication(configuration);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setBannerMode(Banner.Mode.OFF);
        List<String> args = new ArrayList<>(arguments);
        args.add("--spring.config.location=optional:classpath:/no-such-config/");

        assertThatThrownBy(() -> application.run(args.toArray(String[]::new))).isInstanceOf(RuntimeException.class);
        assertThat(output.getOut()).contains("APPLICATION FAILED TO START").contains(properties);
    }
}
