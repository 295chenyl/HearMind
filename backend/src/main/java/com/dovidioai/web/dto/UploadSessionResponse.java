package com.dovidioai.web.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class UploadSessionResponse {

    private String sessionId;
    private String filename;
    private Long fileSize;
    private Integer chunkSize;
    private Integer totalChunks;
    private List<Integer> uploadedChunks;
    private String status;
    private Long videoId;
    private LocalDateTime expiresAt;
}
