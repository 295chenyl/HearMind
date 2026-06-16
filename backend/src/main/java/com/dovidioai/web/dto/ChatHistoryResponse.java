package com.dovidioai.web.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ChatHistoryResponse {

    private Long sessionId;
    private List<ChatMessageResponse> history;
}
