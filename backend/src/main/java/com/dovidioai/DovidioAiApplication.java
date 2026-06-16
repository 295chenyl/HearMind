package com.dovidioai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class DovidioAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(DovidioAiApplication.class, args);
    }
}
