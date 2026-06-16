package com.dovidioai.service.dashscope;

import com.dovidioai.config.DashScopeProperties;
import com.dovidioai.domain.enums.ContentType;
import com.dovidioai.exception.BusinessException;
import com.dovidioai.service.TranscriptIndexService.RetrievedChunk;
import com.dovidioai.support.ExponentialBackoffRetry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashScopeLlmService {

    private final DashScopeProperties properties;
    private final ObjectMapper objectMapper;
    private final ExponentialBackoffRetry retry;
    private final RestClient restClient = RestClient.create();

    public String generateSummary(String videoTitle, String transcript, ContentType contentType) {
        return chat(buildSummarySystemPrompt(contentType), List.of(Map.of(
                "role", "user",
                "content", "视频标题：" + videoTitle + "\n\n转写文本：\n" + truncate(transcript, 120000)
        )));
    }

    public String chatWithRagContext(String videoTitle, String summary, List<RetrievedChunk> chunks,
                                     List<Map<String, String>> history, String userMessage) {
        StringBuilder context = new StringBuilder();
        context.append("视频标题：").append(videoTitle).append("\n\n");
        if (summary != null && !summary.isBlank()) {
            context.append("视频摘要：\n").append(truncate(summary, 8000)).append("\n\n");
        }
        if (chunks != null && !chunks.isEmpty()) {
            context.append("相关转写片段（回答时请引用 [MM:SS] 格式时间戳）：\n");
            for (RetrievedChunk chunk : chunks) {
                context.append('[').append(formatTime(chunk.startSec())).append('-')
                        .append(formatTime(chunk.endSec())).append("] ")
                        .append(chunk.text()).append('\n');
            }
        } else {
            context.append("（未检索到相关转写片段）\n");
        }

        String systemPrompt = """
                你是视频问答助手。只能基于下面提供的摘要与转写片段回答问题。
                如果内容中没有相关信息，请明确说明「转写中未提及」，不要编造。
                回答时尽量引用 [MM:SS] 格式的时间戳。
                
                %s
                """.formatted(context);

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.addAll(history);
        messages.add(Map.of("role", "user", "content", userMessage));
        return chatRaw(messages);
    }

    private String buildSummarySystemPrompt(ContentType contentType) {
        ContentType type = contentType != null ? contentType : ContentType.GENERAL;
        return switch (type) {
            case CLASS -> """
                    你是一名课堂笔记助手。根据转写生成结构化中文摘要，包含：
                    1. 主题
                    2. 要点（条目列表）
                    3. 例题/结论（如有）
                    4. 复习建议
                    不要编造转写中不存在的信息。
                    """;
            case MEETING -> """
                    你是一名会议纪要助手。根据转写生成结构化中文摘要，包含：
                    1. 背景
                    2. 决议
                    3. Action Items
                    4. 风险
                    不要编造转写中不存在的信息。
                    """;
            case INTERVIEW -> """
                    你是一名面试复盘助手。根据转写生成结构化中文摘要，包含：
                    1. 概况
                    2. 技术要点
                    3. 亮点与疑虑
                    4. 结论
                    不要编造转写中不存在的信息。
                    """;
            case GENERAL -> """
                    你是一名专业的视频内容分析助手。请根据提供的视频转写文本，生成结构化中文摘要。
                    要求：
                    1. 用简洁清晰的中文
                    2. 包含：核心主题、关键要点（条目列表）、结论或行动建议（如有）
                    3. 不要编造转写文本中不存在的信息
                    """;
        };
    }

    private String chat(String systemPrompt, List<Map<String, String>> messages) {
        List<Map<String, String>> all = new ArrayList<>();
        all.add(Map.of("role", "system", "content", systemPrompt));
        all.addAll(messages);
        return chatRaw(all);
    }

    private String chatRaw(List<Map<String, String>> messages) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            properties.validateApiKeyConfigured();
        }

        Map<String, Object> input = new HashMap<>();
        input.put("messages", messages);

        Map<String, Object> body = new HashMap<>();
        body.put("model", properties.getLlmModel());
        body.put("input", input);
        body.put("parameters", Map.of("result_format", "message"));

        String response = retry.execute("DashScope LLM", () -> restClient.post()
                .uri(properties.getApiBaseUrl() + "/services/aigc/text-generation/generation")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .retrieve()
                .body(String.class));

        try {
            JsonNode root = objectMapper.readTree(response);
            if (root.has("code") && !root.path("code").asText("").isBlank()) {
                throw new BusinessException("LLM 调用失败: " + root.path("message").asText(response));
            }
            JsonNode content = root.path("output").path("choices").path(0).path("message").path("content");
            if (content.isTextual()) {
                return content.asText("");
            }
            if (content.isArray() && !content.isEmpty()) {
                return content.get(0).path("text").asText(content.toString());
            }
            return root.path("output").path("text").asText("");
        } catch (IOException e) {
            throw new BusinessException("解析 LLM 响应失败: " + e.getMessage());
        }
    }

    private String formatTime(int totalSec) {
        int minutes = totalSec / 60;
        int seconds = totalSec % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private String truncate(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen) + "\n...(内容已截断)";
    }
}
