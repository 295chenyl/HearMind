package com.dovidioai.web.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TranscriptSegmentResponse {

    private long startMs;
    private long endMs;
    private String text;
}
