package com.dovidioai.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UploadInitRequest {

    @NotBlank(message = "文件名不能为空")
    private String filename;

    @NotNull(message = "文件大小不能为空")
    @Min(value = 1, message = "文件大小无效")
    private Long fileSize;

    @NotNull(message = "分片大小不能为空")
    @Min(value = 1048576, message = "分片大小至少 1MB")
    private Integer chunkSize;

    private String contentHash;
}
