package com.example.clothesstoreagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.example.clothesstoreagent.simple")
public class ClothesStoreAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClothesStoreAgentApplication.class, args);
    }
}
