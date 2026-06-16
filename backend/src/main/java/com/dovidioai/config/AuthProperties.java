package com.dovidioai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "auth")
public class AuthProperties {

    private String jwtSecret = "dovideoai-dev-secret-change-in-production";
    private long jwtExpireDays = 7;
}
