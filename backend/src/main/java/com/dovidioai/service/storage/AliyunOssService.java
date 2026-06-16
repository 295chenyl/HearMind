package com.dovidioai.service.storage;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.OSSObject;
import com.dovidioai.config.OssProperties;
import com.dovidioai.exception.BusinessException;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Date;

@Slf4j
@Service
public class AliyunOssService {

    private final OssProperties properties;
    private volatile OSS client;

    public AliyunOssService(OssProperties properties) {
        this.properties = properties;
    }

    public boolean isEnabled() {
        return properties.isEnabled()
                && properties.getEndpoint() != null && !properties.getEndpoint().isBlank()
                && properties.getBucketName() != null && !properties.getBucketName().isBlank()
                && properties.getAccessKeyId() != null && !properties.getAccessKeyId().isBlank()
                && properties.getAccessKeySecret() != null && !properties.getAccessKeySecret().isBlank();
    }

    public String objectKeyForVideo(Long videoId, String filename) {
        String prefix = properties.getObjectPrefix();
        if (prefix == null) {
            prefix = "";
        }
        if (!prefix.isEmpty() && !prefix.endsWith("/")) {
            prefix = prefix + "/";
        }
        return prefix + "videos/" + videoId + "/" + filename;
    }

    public void uploadFile(Path localFile, String objectKey) {
        ensureClient();
        try (InputStream in = Files.newInputStream(localFile)) {
            client.putObject(properties.getBucketName(), objectKey, in);
            log.info("已上传 OSS: {}", objectKey);
        } catch (Exception e) {
            throw new BusinessException("上传 OSS 失败: " + e.getMessage());
        }
    }

    public void uploadStream(InputStream inputStream, String objectKey, long contentLength) {
        ensureClient();
        try {
            client.putObject(properties.getBucketName(), objectKey, inputStream);
            log.info("已上传 OSS 流: {}", objectKey);
        } catch (Exception e) {
            throw new BusinessException("上传 OSS 失败: " + e.getMessage());
        }
    }

    public Path downloadToLocal(String objectKey, Path target) {
        ensureClient();
        try {
            Files.createDirectories(target.getParent());
            try (OSSObject object = client.getObject(properties.getBucketName(), objectKey);
                 InputStream in = object.getObjectContent()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return target;
        } catch (Exception e) {
            throw new BusinessException("从 OSS 下载失败: " + e.getMessage());
        }
    }

    public String generateSignedUrl(String objectKey) {
        ensureClient();
        try {
            Date expiration = Date.from(Instant.now().plusSeconds(properties.getSignUrlExpireSeconds()));
            URL url = client.generatePresignedUrl(properties.getBucketName(), objectKey, expiration);
            return url.toString();
        } catch (Exception e) {
            throw new BusinessException("生成 OSS 签名 URL 失败: " + e.getMessage());
        }
    }

    private void ensureClient() {
        if (!isEnabled()) {
            throw new BusinessException("OSS 未启用或配置不完整");
        }
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    client = new OSSClientBuilder().build(
                            properties.getEndpoint(),
                            properties.getAccessKeyId(),
                            properties.getAccessKeySecret()
                    );
                }
            }
        }
    }

    @PreDestroy
    public void shutdown() {
        if (client != null) {
            client.shutdown();
        }
    }
}
