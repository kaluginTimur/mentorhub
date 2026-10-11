package com.mentorhub;

import org.springframework.boot.SpringApplication;

public class TestMentorHubApplication {

    public static void main(String[] args) {
        SpringApplication.from(MentorHubApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
