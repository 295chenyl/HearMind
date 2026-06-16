package com.dovidioai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "oss")
public class OssProperties {

    private boolean enabled = false;
    private String endpoint = "";
    private String accessKeyId = "";
    private String accessKeySecret = "";
    private String bucketName = "";
    private String objectPrefix = "hearmind/";
    /** 播放签名 URL 有效期（秒） */
    private long signUrlExpireSeconds = 3600;
}
