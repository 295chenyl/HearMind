package com.dovidioai.web;

import com.dovidioai.service.ChatService;
import com.dovidioai.web.dto.ChatHistoryResponse;
import com.dovidioai.web.dto.ChatMessageResponse;
import com.dovidioai.web.dto.ChatRequest;
import com.dovidioai.web.dto.ChatResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/videos/{videoId}/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping
    public ChatResponse chat(@PathVariable Long videoId, @Valid @RequestBody ChatRequest request) {
        return chatService.chat(videoId, request);
    }

    @GetMapping("/history")
    public ChatHistoryResponse history(@PathVariable Long videoId) {
        return chatService.getLatestChatHistory(videoId);
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public List<ChatMessageResponse> messages(@PathVariable Long videoId, @PathVariable Long sessionId) {
        return chatService.getSessionMessages(sessionId);
    }
}
