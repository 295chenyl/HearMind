package com.dovidioai.security;

import com.dovidioai.config.RateLimitProperties;
import com.dovidioai.redis.RedisTokenBucketRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RedisTokenBucketRateLimiter rateLimiter;
    private final RateLimitProperties properties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!properties.isEnabled()) {
            return true;
        }
        String rule = resolveRule(request);
        if (rule == null) {
            return true;
        }
        String bucketKey = resolveBucketKey(request, rule);
        rateLimiter.acquire(bucketKey, rule);
        return true;
    }

    private String resolveRule(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String method = request.getMethod();
        if ("POST".equals(method) && (pathMatcher.match("/api/videos/*/chat", uri)
                || pathMatcher.match("/api/videos/*/chat/stream", uri))) {
            return "chat";
        }
        if ("POST".equals(method) && pathMatcher.match("/api/videos/import-url", uri)) {
            return "import-url";
        }
        // 仅限制整文件上传与会话的创建/完成；分片 PUT 与状态查询不计入，避免大文件续传误触发限流
        if ("POST".equals(method) && pathMatcher.match("/api/videos/upload", uri)) {
            return "upload";
        }
        if ("POST".equals(method) && pathMatcher.match("/api/videos/upload/init", uri)) {
            return "upload";
        }
        if ("POST".equals(method) && pathMatcher.match("/api/videos/upload/*/complete", uri)) {
            return "upload";
        }
        if ("POST".equals(method) && pathMatcher.match("/api/auth/login", uri)) {
            return "login";
        }
        return null;
    }

    private String resolveBucketKey(HttpServletRequest request, String rule) {
        if ("login".equals(rule)) {
            return "ip:" + clientIp(request);
        }
        UserContext.AuthUser user = UserContext.get();
        if (user != null) {
            return "user:" + user.userId();
        }
        return "ip:" + clientIp(request);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
