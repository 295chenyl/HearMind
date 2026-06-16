package com.dovidioai.service.dashscope;

import com.dovidioai.config.DashScopeProperties;
import com.dovidioai.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.dovidioai.support.ExponentialBackoffRetry;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.nio.file.Path;

@Service
@RequiredArgsConstructor
public class DashScopeUploadService {

    private final DashScopeProperties properties;
    private final ObjectMapper objectMapper;
    private final ExponentialBackoffRetry retry;
    private final RestClient restClient = RestClient.create();

    public String uploadAndGetOssUrl(Path filePath) {
        validateApiKey();
        JsonNode policy = fetchUploadPolicy();
        uploadToOss(policy, filePath);
        String uploadDir = policy.path("upload_dir").asText();
        return "oss://" + uploadDir + "/" + filePath.getFileName().toString();
    }

    private void validateApiKey() {
        properties.validateApiKeyConfigured();
    }

    private JsonNode fetchUploadPolicy() {
        String url = properties.getApiBaseUrl() + "/uploads?action=getPolicy&model=" + properties.getAsrModel();
        String body = retry.execute("DashScope upload policy", () -> restClient.get()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .body(String.class));

        try {
            JsonNode root = objectMapper.readTree(body);
            if (root.has("code") && !root.path("code").asText("").isBlank()) {
                throw new BusinessException("获取 DashScope 上传凭证失败: " + root.path("message").asText(body));
            }
            return root.path("data");
        } catch (Exception e) {
            throw new BusinessException("解析上传凭证失败: " + e.getMessage());
        }
    }

    private void uploadToOss(JsonNode policy, Path filePath) {
        String uploadHost = policy.path("upload_host").asText();
        String key = policy.path("upload_dir").asText() + "/" + filePath.getFileName().toString();

        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("OSSAccessKeyId", policy.path("oss_access_key_id").asText());
        form.add("Signature", policy.path("signature").asText());
        form.add("policy", policy.path("policy").asText());
        form.add("x-oss-object-acl", policy.path("x_oss_object_acl").asText("private"));
        form.add("x-oss-forbid-overwrite", policy.path("x_oss_forbid_overwrite").asText("true"));
        form.add("key", key);
        form.add("success_action_status", "200");
        form.add("file", new FileSystemResource(filePath));

        retry.run("DashScope audio upload", () -> restClient.post()
                .uri(uploadHost)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form)
                .retrieve()
                .toBodilessEntity());
    }
}
