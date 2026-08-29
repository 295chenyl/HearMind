package com.dovidioai.web;

import com.dovidioai.service.ChatService;
import com.dovidioai.service.ChatService.ChatStreamContext;
import com.dovidioai.web.dto.CitationResponse;
import com.dovidioai.web.dto.ChatHistoryResponse;
import com.dovidioai.web.dto.ChatMessageResponse;
import com.dovidioai.web.dto.ChatRequest;
import com.dovidioai.web.dto.ChatResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.concurrent.Executor;

@Slf4j
@RestController
@RequestMapping("/api/videos/{videoId}/chat")
public class ChatController {

    private static final long STREAM_TIMEOUT_MS = 180_000L;

    private final ChatService chatService;
    private final Executor chatTaskExecutor;

    public ChatController(ChatService chatService,
                          @Qualifier("chatTaskExecutor") Executor chatTaskExecutor) {
        this.chatService = chatService;
        this.chatTaskExecutor = chatTaskExecutor;
    }

    @PostMapping
    public ChatResponse chat(@PathVariable Long videoId, @Valid @RequestBody ChatRequest request) {
        return chatService.chat(videoId, request);
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable Long videoId, @Valid @RequestBody ChatRequest request) {
        ChatStreamContext context = chatService.prepareChatStream(videoId, request);
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        chatTaskExecutor.execute(() -> streamResponse(videoId, context, emitter));
        return emitter;
    }

    @GetMapping("/history")
    public ChatHistoryResponse history(@PathVariable Long videoId) {
        return chatService.getLatestChatHistory(videoId);
    }

    @GetMapping("/sessions/{sessionId}/messages")
    public List<ChatMessageResponse> messages(@PathVariable Long videoId, @PathVariable Long sessionId) {
        return chatService.getSessionMessages(sessionId);
    }

    private void streamResponse(Long videoId, ChatStreamContext context, SseEmitter emitter) {
        try {
            sendEvent(emitter, "meta", new StreamMetadata(context.sessionId(), context.citations()));
            ChatResponse response = chatService.streamChat(
                    context,
                    chunk -> sendEvent(emitter, "delta", new StreamDelta(chunk))
            );
            sendEvent(emitter, "done", response);
            emitter.complete();
        } catch (Exception e) {
            log.warn("Chat stream failed, videoId={}, sessionId={}: {}",
                    videoId, context.sessionId(), e.getMessage());
            completeWithError(emitter, e);
        }
    }

    private void sendEvent(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void completeWithError(SseEmitter emitter, Exception error) {
        try {
            sendEvent(emitter, "error", new StreamError(safeMessage(error)));
            emitter.complete();
        } catch (Exception sendError) {
            emitter.completeWithError(error);
        }
    }

    private String safeMessage(Exception error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return "问答生成失败，请稍后重试";
        }
        return message.length() <= 300 ? message : message.substring(0, 300) + "...";
    }

    private record StreamMetadata(Long sessionId, List<CitationResponse> citations) {
    }

    private record StreamDelta(String content) {
    }

    private record StreamError(String message) {
    }
}
