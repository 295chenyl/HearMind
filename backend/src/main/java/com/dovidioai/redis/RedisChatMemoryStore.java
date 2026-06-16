package com.dovidioai.redis;

import com.dovidioai.config.RedisChatProperties;
import com.dovidioai.exception.BusinessException;
import com.dovidioai.web.dto.ChatMessageResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RedisChatMemoryStore {

    private static final String SESSION_SEQ = "chat:seq:session";
    private static final String MESSAGE_SEQ = "chat:seq:message";

    private final StringRedisTemplate redisTemplate;
    private final RedisChatProperties chatProperties;
    private final ObjectMapper objectMapper;

    public ChatSessionRecord createSession(Long videoId, Long userId, String title) {
        Long sessionId = redisTemplate.opsForValue().increment(SESSION_SEQ);
        if (sessionId == null) {
            throw new BusinessException("创建会话失败");
        }
        LocalDateTime now = LocalDateTime.now();
        ChatSessionRecord session = new ChatSessionRecord(sessionId, videoId, userId, title, now);
        saveSession(session);
        setLatestSession(userId, videoId, session.id());
        return session;
    }

    public Optional<ChatSessionRecord> findSession(Long sessionId) {
        String json = redisTemplate.opsForValue().get(sessionKey(sessionId));
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, ChatSessionRecord.class));
        } catch (Exception e) {
            throw new BusinessException("读取会话失败: " + e.getMessage());
        }
    }

    public Optional<Long> findLatestSessionId(Long userId, Long videoId) {
        String value = redisTemplate.opsForValue().get(latestKey(userId, videoId));
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(Long.parseLong(value));
    }

    public ChatMessageResponse appendMessage(Long sessionId, String role, String content) {
        Long messageId = redisTemplate.opsForValue().increment(MESSAGE_SEQ);
        if (messageId == null) {
            throw new BusinessException("保存消息失败");
        }
        LocalDateTime now = LocalDateTime.now();
        ChatMessageRecord record = new ChatMessageRecord(messageId, role, content, now);
        try {
            redisTemplate.opsForList().rightPush(messagesKey(sessionId), objectMapper.writeValueAsString(record));
        } catch (Exception e) {
            throw new BusinessException("保存消息失败: " + e.getMessage());
        }
        refreshTtl(sessionId);
        return ChatMessageResponse.builder()
                .id(messageId)
                .role(role)
                .content(content)
                .createdAt(now)
                .build();
    }

    public List<ChatMessageResponse> listMessages(Long sessionId) {
        List<String> raw = redisTemplate.opsForList().range(messagesKey(sessionId), 0, -1);
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<ChatMessageResponse> result = new ArrayList<>();
        for (String item : raw) {
            try {
                ChatMessageRecord record = objectMapper.readValue(item, ChatMessageRecord.class);
                result.add(ChatMessageResponse.builder()
                        .id(record.id())
                        .role(record.role())
                        .content(record.content())
                        .createdAt(record.createdAt())
                        .build());
            } catch (Exception e) {
                throw new BusinessException("读取消息失败: " + e.getMessage());
            }
        }
        return result;
    }

    public List<ChatMessageResponse> listMessagesBeforeLast(Long sessionId) {
        List<ChatMessageResponse> all = listMessages(sessionId);
        if (all.size() <= 1) {
            return List.of();
        }
        return all.subList(0, all.size() - 1);
    }

    private void saveSession(ChatSessionRecord session) {
        try {
            redisTemplate.opsForValue().set(sessionKey(session.id()), objectMapper.writeValueAsString(session), ttl());
        } catch (Exception e) {
            throw new BusinessException("保存会话失败: " + e.getMessage());
        }
        refreshTtl(session.id());
    }

    private void setLatestSession(Long userId, Long videoId, Long sessionId) {
        redisTemplate.opsForValue().set(latestKey(userId, videoId), String.valueOf(sessionId), ttl());
    }

    private void refreshTtl(Long sessionId) {
        redisTemplate.expire(sessionKey(sessionId), ttl());
        redisTemplate.expire(messagesKey(sessionId), ttl());
    }

    private Duration ttl() {
        return Duration.ofDays(Math.max(1, chatProperties.getTtlDays()));
    }

    private String sessionKey(Long sessionId) {
        return "chat:session:" + sessionId;
    }

    private String messagesKey(Long sessionId) {
        return "chat:messages:" + sessionId;
    }

    private String latestKey(Long userId, Long videoId) {
        return "chat:latest:" + userId + ":" + videoId;
    }

    public record ChatSessionRecord(Long id, Long videoId, Long userId, String title, LocalDateTime createdAt) {
    }

    public record ChatMessageRecord(Long id, String role, String content, LocalDateTime createdAt) {
    }
}
