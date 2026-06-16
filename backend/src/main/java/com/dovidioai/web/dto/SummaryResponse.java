package com.dovidioai.web.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SummaryResponse {

    private Long videoId;
    private String content;
    private String model;
    private boolean userEdited;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
