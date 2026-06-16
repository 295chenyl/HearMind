package com.dovidioai.redis;

import com.dovidioai.config.RateLimitProperties;
import com.dovidioai.exception.RateLimitExceededException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RedisTokenBucketRateLimiter {

    private static final DefaultRedisScript<Long> TOKEN_BUCKET_SCRIPT = new DefaultRedisScript<>();

    static {
        TOKEN_BUCKET_SCRIPT.setLocation(new ClassPathResource("redis/token_bucket.lua"));
        TOKEN_BUCKET_SCRIPT.setResultType(Long.class);
    }

    private final StringRedisTemplate redisTemplate;
    private final RateLimitProperties properties;

    public void acquire(String bucketKey, String ruleName) {
        if (!properties.isEnabled()) {
            return;
        }
        RateLimitProperties.BucketRule rule = properties.getRules().getOrDefault(
                ruleName,
                properties.getRules().get("default")
        );
        if (rule == null) {
            return;
        }

        String redisKey = "rate:" + bucketKey + ":" + ruleName;
        long nowMillis = System.currentTimeMillis();
        Long allowed = redisTemplate.execute(
                TOKEN_BUCKET_SCRIPT,
                List.of(redisKey),
                String.valueOf(rule.getCapacity()),
                String.valueOf(rule.getRefillPerSecond()),
                String.valueOf(nowMillis),
                "1"
        );
        if (allowed == null || allowed == 0L) {
            throw new RateLimitExceededException("请求过于频繁，请稍后再试");
        }
    }
}
