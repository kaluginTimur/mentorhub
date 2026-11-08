package com.mentorhub.support;

import com.mentorhub.TestcontainersConfiguration;
import com.mentorhub.users.internal.security.TestIssuerConfiguration;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

/**
 * The whole application behind MockMvc, with tokens signed by {@code TestIssuer}, for endpoint tests: what crosses
 * layers and authentication through real tokens. Every class with this annotation and nothing else that configures the
 * context shares one context.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, TestIssuerConfiguration.class})
public @interface EndpointTest {}
