package com.dovidioai.web.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ImportUrlPreviewResponse {

    private String title;
    private Integer durationSec;
    private String platform;
    private String platformVideoId;
    private List<String> warnings;
}
