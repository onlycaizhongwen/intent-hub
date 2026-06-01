package com.intenthub.interfaces;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.intenthub")
public class IntentHubApplication {
    public static void main(String[] args) {
        SpringApplication.run(IntentHubApplication.class, args);
    }
}
