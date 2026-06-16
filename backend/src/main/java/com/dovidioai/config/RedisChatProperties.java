package com.dovidioai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "chat.redis")
public class RedisChatProperties {

    /** 会话与消息在 Redis 中的 TTL（天） */
    private int ttlDays = 30;
}
