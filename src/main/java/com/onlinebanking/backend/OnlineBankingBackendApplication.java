package com.onlinebanking.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class OnlineBankingBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(OnlineBankingBackendApplication.class, args);
    }
}
