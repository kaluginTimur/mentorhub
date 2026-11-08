package com.mentorhub.support;

import com.mentorhub.users.internal.security.WebSliceSecurity;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;

/**
 * The web slice of one controller with the application's filter chains, gate and method security, for the operation's
 * permission, request binding, validation before the service and the JSON of its responses. Authenticate requests with
 * {@code TestAuthentications}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@WebMvcTest
@Import(WebSliceSecurity.class)
public @interface ControllerSliceTest {

    /** The controller under test. */
    @AliasFor(annotation = WebMvcTest.class, attribute = "controllers")
    Class<?>[] value();

    /** Properties added to the environment of the slice. */
    @AliasFor(annotation = WebMvcTest.class, attribute = "properties")
    String[] properties() default {};
}
