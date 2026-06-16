package com.dovidioai.web.dto;

import com.dovidioai.domain.enums.ContentType;
import com.dovidioai.domain.enums.SourceType;
import com.dovidioai.domain.enums.VideoStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class VideoResponse {

    private Long id;
    private String title;
    private SourceType sourceType;
    private String sourceUrl;
    private String platform;
    private ContentType contentType;
    private Integer durationSec;
    private Long fileSize;
    private VideoStatus status;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /** 命中去重时为 true，id 指向已存在的视频 */
    private Boolean deduplicated;
}
