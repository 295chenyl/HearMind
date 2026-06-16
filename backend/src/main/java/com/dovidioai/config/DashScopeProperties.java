package com.dovidioai.config;

import com.dovidioai.exception.BusinessException;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "dashscope")
public class DashScopeProperties implements InitializingBean {

    private String apiKey = "";
    private String keyFile = "./config/dashscope.key";
    private String asrModel = "paraformer-v2";
    private String llmModel = "qwen-plus";
    private String apiBaseUrl = "https://dashscope.aliyuncs.com/api/v1";

    @Override
    public void afterPropertiesSet() {
        loadApiKeyFromFile();
    }

    public void loadApiKeyFromFile() {
        Path path = Path.of(keyFile).toAbsolutePath().normalize();
        String fromFile = readKeyFromFile(path);
        if (fromFile != null) {
            this.apiKey = fromFile;
            log.info("已从文件加载 DashScope API Key: {}", path);
            return;
        }
        if (isInvalidKey(apiKey)) {
            this.apiKey = "";
            log.warn("DashScope API Key 无效或未配置，请编辑 {}", path);
        }
    }

    private String readKeyFromFile(Path path) {
        if (!Files.exists(path)) {
            log.warn("DashScope Key 文件不存在: {}", path);
            return null;
        }
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                if (!isInvalidKey(trimmed)) {
                    return trimmed;
                }
            }
        } catch (IOException e) {
            throw new BusinessException("读取 DashScope Key 文件失败: " + e.getMessage());
        }
        return null;
    }

    private boolean isInvalidKey(String key) {
        if (key == null || key.isBlank()) {
            return true;
        }
        return key.contains("请替换") || key.contains("your-api-key") || key.equals("sk-xxx");
    }

    public void validateApiKeyConfigured() {
        if (isInvalidKey(apiKey)) {
            throw new BusinessException(
                    "未配置有效的 DashScope API Key。请编辑 config/dashscope.key，只保留一行 sk- 开头的 Key");
        }
    }
}
