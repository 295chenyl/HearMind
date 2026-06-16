package com.dovidioai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Configuration
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;

    /** 规则名 -> 令牌桶配置（capacity + 每秒补充令牌数） */
    private Map<String, BucketRule> rules = defaultRules();

    private static Map<String, BucketRule> defaultRules() {
        Map<String, BucketRule> map = new LinkedHashMap<>();
        map.put("chat", new BucketRule(10, 0.5));
        map.put("import-url", new BucketRule(5, 0.2));
        map.put("upload", new BucketRule(5, 0.2));
        map.put("login", new BucketRule(10, 0.3));
        map.put("default", new BucketRule(60, 10));
        return map;
    }

    @Data
    public static class BucketRule {
        private double capacity;
        private double refillPerSecond;

        public BucketRule() {
        }

        public BucketRule(double capacity, double refillPerSecond) {
            this.capacity = capacity;
            this.refillPerSecond = refillPerSecond;
        }
    }
}
