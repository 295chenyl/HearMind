package com.dovidioai.support;

import com.dovidioai.config.RetryProperties;
import com.dovidioai.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExponentialBackoffRetry {

    private final RetryProperties properties;

    public <T> T execute(String operation, Supplier<T> action) {
        int attempt = 0;
        long delayMs = properties.getInitialDelayMs();
        RuntimeException lastError = null;

        while (attempt < properties.getMaxAttempts()) {
            attempt++;
            try {
                return action.get();
            } catch (RuntimeException e) {
                lastError = e;
                if (!isRetryable(e) || attempt >= properties.getMaxAttempts()) {
                    throw e;
                }
                log.warn("{} 第 {}/{} 次失败，{}ms 后重试: {}",
                        operation, attempt, properties.getMaxAttempts(), delayMs, rootMessage(e));
                sleep(delayMs);
                delayMs = Math.min((long) (delayMs * properties.getMultiplier()), properties.getMaxDelayMs());
            }
        }
        throw lastError != null ? lastError : new BusinessException(operation + " 失败");
    }

    public void run(String operation, Runnable action) {
        execute(operation, () -> {
            action.run();
            return null;
        });
    }

    private boolean isRetryable(RuntimeException e) {
        if (e instanceof ResourceAccessException) {
            return true;
        }
        if (e instanceof RestClientResponseException rest) {
            int status = rest.getStatusCode().value();
            return status >= 500 || status == 429;
        }
        if (e instanceof BusinessException business) {
            String msg = business.getMessage() != null ? business.getMessage().toLowerCase() : "";
            return msg.contains("timeout")
                    || msg.contains("timed out")
                    || msg.contains("connection")
                    || msg.contains("temporarily")
                    || msg.contains("429");
        }
        Throwable cause = e.getCause();
        if (cause instanceof IOException) {
            return true;
        }
        if (cause instanceof RuntimeException runtime) {
            return isRetryable(runtime);
        }
        return false;
    }

    private String rootMessage(Throwable e) {
        Throwable cur = e;
        while (cur.getCause() != null) {
            cur = cur.getCause();
        }
        return cur.getMessage() != null ? cur.getMessage() : e.getClass().getSimpleName();
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("重试等待被中断");
        }
    }
}
