package com.dovidioai.service.dashscope;

import com.dovidioai.config.DashScopeProperties;
import com.dovidioai.exception.BusinessException;
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
public class DashScopeEmbeddingService {

    private static final String EMBEDDING_MODEL = "text-embedding-v3";

    private final DashScopeProperties properties;
    private final ObjectMapper objectMapper;
    private final ExponentialBackoffRetry retry;
    private final RestClient restClient = RestClient.create();

    public List<Double> embed(String text) {
        List<List<Double>> batch = embedBatch(List.of(text));
        return batch.isEmpty() ? List.of() : batch.get(0);
    }

    public List<List<Double>> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        properties.validateApiKeyConfigured();

        Map<String, Object> input = new HashMap<>();
        input.put("texts", texts);

        Map<String, Object> body = new HashMap<>();
        body.put("model", EMBEDDING_MODEL);
        body.put("input", input);

        String response = retry.execute("DashScope Embedding", () -> restClient.post()
                .uri(properties.getApiBaseUrl() + "/services/embeddings/text-embedding/text-embedding")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .retrieve()
                .body(String.class));

        try {
            JsonNode root = objectMapper.readTree(response);
            if (root.has("code") && !root.path("code").asText("").isBlank()) {
                throw new BusinessException("Embedding 调用失败: " + root.path("message").asText(response));
            }
            JsonNode embeddings = root.path("output").path("embeddings");
            if (!embeddings.isArray()) {
                throw new BusinessException("Embedding 响应格式异常");
            }
            List<List<Double>> result = new ArrayList<>();
            for (JsonNode item : embeddings) {
                JsonNode vector = item.path("embedding");
                if (!vector.isArray()) {
                    continue;
                }
                List<Double> values = new ArrayList<>();
                for (JsonNode v : vector) {
                    values.add(v.asDouble());
                }
                result.add(values);
            }
            return result;
        } catch (IOException e) {
            throw new BusinessException("解析 Embedding 响应失败: " + e.getMessage());
        }
    }
}
