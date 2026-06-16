package com.dovidioai.web.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CitationResponse {

    private int startSec;
    private int endSec;
    private String quote;
}
