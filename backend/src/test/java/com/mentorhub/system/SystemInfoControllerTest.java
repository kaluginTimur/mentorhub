package com.mentorhub.system;

import static org.assertj.core.api.Assertions.assertThat;

import com.mentorhub.users.internal.security.WebSliceSecurity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(SystemInfoController.class)
@Import(WebSliceSecurity.class)
class SystemInfoControllerTest {

    @Autowired
    MockMvcTester mvc;

    @Test
    void returnsNameAndVersion() {
        assertThat(mvc.get().uri("/api/v1/system/info"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.name")
                .isEqualTo("MentorHub");
    }
}
