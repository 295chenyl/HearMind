package com.dovidioai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "retry")
public class RetryProperties {

    private int maxAttempts = 3;
    private long initialDelayMs = 1000;
    private double multiplier = 2.0;
    private long maxDelayMs = 10000;
}
