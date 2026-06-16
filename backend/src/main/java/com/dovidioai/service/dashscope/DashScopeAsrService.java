package com.dovidioai.service.dashscope;

import com.dovidioai.config.DashScopeProperties;
import com.dovidioai.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dovidioai.support.ExponentialBackoffRetry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashScopeAsrService {

    public record TranscriptionResult(String fullText, String segmentsJson) {
    }

    private final DashScopeProperties properties;
    private final DashScopeUploadService uploadService;
    private final ObjectMapper objectMapper;
    private final ExponentialBackoffRetry retry;
    private final RestClient restClient = RestClient.create();

    public TranscriptionResult transcribe(Path audioPath) {
        properties.validateApiKeyConfigured();

        String ossUrl = uploadService.uploadAndGetOssUrl(audioPath);
        String taskId = submitTask(ossUrl);
        JsonNode result = pollTask(taskId);
        return parseTranscriptionResult(result);
    }

    private String submitTask(String fileUrl) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", properties.getAsrModel());

        Map<String, Object> input = new HashMap<>();
        input.put("file_urls", List.of(fileUrl));
        body.put("input", input);

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("language_hints", List.of("zh", "en"));
        body.put("parameters", parameters);

        String response = retry.execute("DashScope ASR submit", () -> restClient.post()
                .uri(properties.getApiBaseUrl() + "/services/audio/asr/transcription")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header("X-DashScope-Async", "enable")
                .header("X-DashScope-OssResourceResolve", "enable")
                .body(body)
                .retrieve()
                .body(String.class));

        try {
            JsonNode root = objectMapper.readTree(response);
            if (root.has("code") && !root.path("code").asText("").isBlank()) {
                throw new BusinessException("提交 ASR 任务失败: " + root.path("message").asText(response));
            }
            String taskId = root.path("output").path("task_id").asText(null);
            if (taskId == null || taskId.isBlank()) {
                throw new BusinessException("ASR 任务 ID 为空");
            }
            return taskId;
        } catch (IOException e) {
            throw new BusinessException("解析 ASR 提交响应失败: " + e.getMessage());
        }
    }

    private JsonNode pollTask(String taskId) {
        long deadline = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(30);
        while (System.currentTimeMillis() < deadline) {
            String response = retry.execute("DashScope ASR poll", () -> restClient.get()
                    .uri(properties.getApiBaseUrl() + "/tasks/" + taskId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                    .header("X-DashScope-Async", "enable")
                    .header("X-DashScope-OssResourceResolve", "enable")
                    .retrieve()
                    .body(String.class));

            try {
                JsonNode root = objectMapper.readTree(response);
                JsonNode output = root.path("output");
                String status = output.path("task_status").asText("");
                if ("SUCCEEDED".equals(status)) {
                    validateSubtask(output);
                    return output;
                }
                if ("FAILED".equals(status)) {
                    throw new BusinessException("ASR 任务失败: " + formatAsrError(output.path("message").asText("")));
                }
            } catch (IOException e) {
                throw new BusinessException("解析 ASR 任务状态失败: " + e.getMessage());
            }

            sleep(3000);
        }
        throw new BusinessException("ASR 任务超时");
    }

    private void validateSubtask(JsonNode output) {
        JsonNode results = output.path("results");
        if (!results.isArray() || results.isEmpty()) {
            throw new BusinessException("ASR 结果为空");
        }
        JsonNode first = results.get(0);
        String subtaskStatus = first.path("subtask_status").asText("");
        if (!"SUCCEEDED".equals(subtaskStatus)) {
            String message = first.path("message").asText("");
            throw new BusinessException("ASR 识别失败: " + formatAsrError(message));
        }
    }

    private TranscriptionResult parseTranscriptionResult(JsonNode output) {
        JsonNode first = output.path("results").get(0);
        String transcriptionUrl = first.path("transcription_url").asText(null);
        if (transcriptionUrl == null || transcriptionUrl.isBlank()) {
            throw new BusinessException("未返回 transcription_url");
        }

        String json = retry.execute("DashScope ASR result download",
                () -> downloadTranscriptionJsonOnce(transcriptionUrl));
        try {
            JsonNode root = objectMapper.readTree(json);
            String fullText = extractFullText(root);
            if (fullText.isBlank()) {
                throw new BusinessException("ASR 识别结果为空，请确认视频包含清晰语音");
            }
            return new TranscriptionResult(fullText, json);
        } catch (IOException e) {
            throw new BusinessException("解析 ASR 结果 JSON 失败: " + e.getMessage());
        }
    }

    /**
     * 使用原始 URL 下载，避免 RestClient 重新编码 OSS 预签名参数导致 SignatureDoesNotMatch。
     */
    private String downloadTranscriptionJsonOnce(String transcriptionUrl) {
        try {
            URL url = URI.create(transcriptionUrl).toURL();
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(30_000);
            connection.setReadTimeout(120_000);
            connection.setRequestProperty("Authorization", "Bearer " + properties.getApiKey());
            connection.setRequestProperty("X-DashScope-OssResourceResolve", "enable");

            int statusCode = connection.getResponseCode();
            InputStream stream = statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream();
            if (stream == null) {
                throw new BusinessException("下载转写结果失败: HTTP " + statusCode);
            }
            String body = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        if (statusCode >= 400) {
                log.warn("下载转写结果失败 status={} body={}", statusCode, abbreviate(body));
                if (statusCode >= 500 || statusCode == 429) {
                    throw new org.springframework.web.client.ResourceAccessException(
                            "下载转写结果失败: HTTP " + statusCode);
                }
                throw new BusinessException("下载转写结果失败: HTTP " + statusCode);
            }
            return body;
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            throw new BusinessException("下载转写结果失败: " + e.getMessage());
        }
    }

    private String formatAsrError(String message) {
        if (message == null || message.isBlank()) {
            return "未知错误";
        }
        if (message.contains("SUCCESS_WITH_NO_VALID_FRAGMENT") || message.contains("NO_VALID_FRAGMENT")) {
            return "未检测到有效语音片段，请确认视频包含清晰人声或对白";
        }
        return message;
    }

    private String extractFullText(JsonNode root) {
        if (root.has("transcripts") && root.path("transcripts").isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode transcript : root.path("transcripts")) {
                String text = transcript.path("text").asText("");
                if (!text.isBlank()) {
                    if (!sb.isEmpty()) {
                        sb.append('\n');
                    }
                    sb.append(text.trim());
                }
            }
            if (!sb.isEmpty()) {
                return sb.toString();
            }
        }
        if (root.has("text")) {
            return root.path("text").asText("");
        }
        return root.toString();
    }

    private String abbreviate(String text) {
        return text.length() > 300 ? text.substring(0, 300) + "..." : text;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("ASR 轮询被中断");
        }
    }
}
