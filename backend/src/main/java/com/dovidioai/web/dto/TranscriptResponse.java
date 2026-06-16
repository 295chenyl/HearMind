package com.dovidioai.web.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class TranscriptResponse {

    private Long videoId;
    private String fullText;
    private String segmentsJson;
    private List<TranscriptSegmentResponse> segments;
    private LocalDateTime createdAt;
}
