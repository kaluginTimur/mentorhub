package com.mentorhub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

// Without @EnableAsync, @ApplicationModuleListener methods run in the request thread after the commit.
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class MentorHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(MentorHubApplication.class, args);
    }
}
