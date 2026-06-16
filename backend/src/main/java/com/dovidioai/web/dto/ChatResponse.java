package com.dovidioai.web.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ChatResponse {

    private Long sessionId;
    private String reply;
    private List<CitationResponse> citations;
    private List<ChatMessageResponse> history;
}
