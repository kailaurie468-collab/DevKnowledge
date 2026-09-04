package com.devknowledge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DevKnowledgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(DevKnowledgeApplication.class, args);
    }
}
