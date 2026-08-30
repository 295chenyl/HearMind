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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

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

    public String chatWithRagContext(String videoTitle, String summary, String transcriptExcerpt,
                                     List<RetrievedChunk> chunks,
                                     List<Map<String, String>> history, String userMessage) {
        return chatRaw(buildChatMessages(
                videoTitle, summary, transcriptExcerpt, chunks, history, userMessage
        ));
    }

    public String chatWithRagContextStream(String videoTitle, String summary, String transcriptExcerpt,
                                           List<RetrievedChunk> chunks,
                                           List<Map<String, String>> history, String userMessage,
                                           Consumer<String> chunkConsumer) {
        return chatRawStream(buildChatMessages(
                videoTitle, summary, transcriptExcerpt, chunks, history, userMessage
        ), chunkConsumer);
    }

    private List<Map<String, String>> buildChatMessages(String videoTitle, String summary,
                                                         String transcriptExcerpt,
                                                         List<RetrievedChunk> chunks,
                                                         List<Map<String, String>> history,
                                                         String userMessage) {
        String chunksBlock = buildRetrievedChunksBlock(chunks, transcriptExcerpt);

        String systemPrompt = CHAT_ASSISTANT_PROMPT + """

                # Input Context

                **Video Title**: %s

                <Existing Summary Reference>
                %s

                <Retrieved Text Chunks>
                %s

                说明：<Chat History> 由下方 messages 中的 user/assistant 多轮对话提供；请根据 User Question 自动选择「状态 A」或「状态 B」输出。
                """.formatted(
                videoTitle != null ? videoTitle : "未命名",
                summary != null && !summary.isBlank() ? truncate(summary, 8000) : "（暂无预生成摘要）",
                chunksBlock
        );

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.addAll(history);
        String question = userMessage != null && !userMessage.isBlank() ? userMessage : "请对这篇音视频内容进行总结概括。";
        messages.add(Map.of("role", "user", "content", question));
        return messages;
    }

    private String buildRetrievedChunksBlock(List<RetrievedChunk> chunks, String transcriptExcerpt) {
        if (chunks != null && !chunks.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            int i = 1;
            for (RetrievedChunk chunk : chunks) {
                sb.append(i++).append(". ").append(chunk.text().trim()).append('\n');
            }
            return sb.toString();
        }
        if (transcriptExcerpt != null && !transcriptExcerpt.isBlank()) {
            return truncate(transcriptExcerpt, 120000);
        }
        return "（未检索到相关转写片段）";
    }

    private static final String CHAT_ASSISTANT_PROMPT = """
            # Role
            你是一款顶尖的「音视频内容数字化专家与智能助理」。你能够高效解析各类音视频转写文本（包括会议、课程、新闻、语音备忘录等），并具备强大的结构化总结、精准信息检索和多轮对话记忆能力。

            # Skills
            1. **智能自适应总结**：在首次接收文本或用户未提出具体问题时，能够根据内容类型（如会议、课程、新闻）自动输出最适配的结构化摘要。
            2. **精准 RAG 问答**：结合用户历史对话与当前检索到的文本片段（Chunks），进行严格基于事实的答疑，不胡编乱造。
            3. **文本降噪**：自动过滤 ASR 转写文本中的时间戳、口水词（如「呃」、「啊」、「然后」）以及由于识别错误导致的语病，提炼出流畅、干净的核心观点。

            # Workflow & Output Format
            请根据输入的信息状态，自动选择以下对应流程进行输出：

            ---

            ### 状态 A：首次输入/用户要求总结（User Question 为空或包含「总结/概括/主要讲了什么」）
            请忽略时间戳，直接输出一份清晰、美观的结构化摘要，格式如下：

            ## 📌 内容概览
            - **内容类型评估**：[识别是会议/课程/新闻/其他]
            - **核心主题**：[用一句话概括这篇音视频的核心主旨]

            ## 📝 核心要点提炼
            > 请根据实际内容拆分 3-5 个核心模块或议题：
            1. **[主题要点一]**：[详细描述、核心结论或关键数据]
            2. **[主题要点二]**：[详细描述、核心结论或关键数据]
            3. **[主题要点三]**：[详细描述、核心结论或关键数据]

            ## 💡 关键行动项 / 核心启示
            - [针对会议：列出待办事项、负责人（若有）；针对课程：列出核心公式/定理/概念；针对新闻/记录：列出核心结论或后续延伸]

            ---

            ### 状态 B：多轮问答与 RAG 检索（User Question 包含具体问题）
            当用户针对内容进行提问时，请结合 `<Chat History>` 和 `<Retrieved Text Chunks>` 遵循以下规则回答：

            1. **事实优先**：必须严格基于 `<Retrieved Text Chunks>` 提供的上下文进行回答。
            2. **拒绝幻觉**：如果检索到的文本片段中不包含答案，请礼貌地回答：「抱歉，在当前的音视频片段中没有找到相关内容，您可以尝试换个问法或提供更多上下文。」
            3. **无需引用时间戳**：在回答中请直接陈述事实，**不要**在对话中夹带 `[00:12:34]` 等时间戳标识，保持回答的连贯与整洁。
            4. **结合语境**：参考 `<Chat History>` 确保指代消解正确（例如用户问「他刚才说的那个方法是什么？」，需从历史记录中识别「他」和「那个方法」指代什么）。

            # Constraints
            - 保持客观、专业、条理清晰的语气。
            - 无论原文多么冗长凌乱，输出必须具有高度的可读性和逻辑性。
            - 严格禁止编造原文中没有出现过的数据、人名和结论。
            """;

    public boolean isSummaryStyleQuestion(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return true;
        }
        String m = userMessage.trim();
        return m.contains("总结") || m.contains("概括") || m.contains("主要讲了什么")
                || m.contains("讲了什么") || m.contains("内容概览") || m.contains("摘要")
                || m.contains("主要内容") || m.contains("核心要点");
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
        validateApiKey();

        String response = retry.execute("DashScope LLM", () -> restClient.post()
                .uri(properties.getApiBaseUrl() + "/services/aigc/text-generation/generation")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(buildRequestBody(messages, false))
                .retrieve()
                .body(String.class));

        try {
            return extractResponseContent(objectMapper.readTree(response), response);
        } catch (IOException e) {
            throw new BusinessException("解析 LLM 响应失败: " + e.getMessage());
        }
    }

    private String chatRawStream(List<Map<String, String>> messages, Consumer<String> chunkConsumer) {
        validateApiKey();
        return restClient.post()
                .uri(properties.getApiBaseUrl() + "/services/aigc/text-generation/generation")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE)
                .header("X-DashScope-SSE", "enable")
                .body(buildRequestBody(messages, true))
                .exchange((request, response) -> readStreamResponse(response, chunkConsumer));
    }

    private String readStreamResponse(org.springframework.http.client.ClientHttpResponse response,
                                      Consumer<String> chunkConsumer) {
        try {
            if (!response.getStatusCode().is2xxSuccessful()) {
                String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                throw new BusinessException("LLM 调用失败: " + body);
            }

            StringBuilder reply = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    response.getBody(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) {
                        continue;
                    }
                    String delta = parseStreamData(line.substring(5).trim());
                    if (!delta.isEmpty()) {
                        reply.append(delta);
                        chunkConsumer.accept(delta);
                    }
                }
            }
            if (reply.isEmpty()) {
                throw new BusinessException("LLM 流式响应为空");
            }
            return reply.toString();
        } catch (IOException e) {
            throw new BusinessException("读取 LLM 流式响应失败: " + e.getMessage());
        }
    }

    String parseStreamData(String data) {
        if (data == null || data.isBlank() || "[DONE]".equals(data)) {
            return "";
        }
        try {
            return extractResponseContent(objectMapper.readTree(data), data);
        } catch (IOException e) {
            throw new BusinessException("解析 LLM 流式响应失败: " + e.getMessage());
        }
    }

    private String extractResponseContent(JsonNode root, String rawResponse) {
        if (root.has("code") && !root.path("code").asText("").isBlank()) {
            throw new BusinessException("LLM 调用失败: " + root.path("message").asText(rawResponse));
        }
        JsonNode content = root.path("output").path("choices").path(0).path("message").path("content");
        if (content.isTextual()) {
            return content.asText("");
        }
        if (content.isArray() && !content.isEmpty()) {
            return content.get(0).path("text").asText(content.toString());
        }
        return root.path("output").path("text").asText("");
    }

    private Map<String, Object> buildRequestBody(List<Map<String, String>> messages, boolean streaming) {
        Map<String, Object> input = new HashMap<>();
        input.put("messages", messages);

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("result_format", "message");
        if (streaming) {
            parameters.put("incremental_output", true);
        }

        Map<String, Object> body = new HashMap<>();
        body.put("model", properties.getLlmModel());
        body.put("input", input);
        body.put("parameters", parameters);
        return body;
    }

    private void validateApiKey() {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            properties.validateApiKeyConfigured();
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
