package com.dovidioai.service;

import com.dovidioai.domain.entity.Summary;
import com.dovidioai.domain.entity.Transcript;
import com.dovidioai.domain.entity.Video;
import com.dovidioai.domain.enums.VideoStatus;
import com.dovidioai.domain.repository.SummaryRepository;
import com.dovidioai.domain.repository.TranscriptRepository;
import com.dovidioai.domain.repository.VideoRepository;
import com.dovidioai.exception.BusinessException;
import com.dovidioai.redis.RedisChatMemoryStore;
import com.dovidioai.redis.RedisChatMemoryStore.ChatSessionRecord;
import com.dovidioai.service.dashscope.DashScopeLlmService;
import com.dovidioai.web.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final VideoRepository videoRepository;
    private final TranscriptRepository transcriptRepository;
    private final SummaryRepository summaryRepository;
    private final RedisChatMemoryStore chatMemoryStore;
    private final DashScopeLlmService llmService;
    private final TranscriptIndexService transcriptIndexService;
    private final CurrentUserService currentUserService;

    public ChatResponse chat(Long videoId, ChatRequest request) {
        Video video = getReadyVideo(videoId);
        Transcript transcript = transcriptRepository.findByVideoId(videoId)
                .orElseThrow(() -> new BusinessException("转写内容不存在"));

        ChatSessionRecord session = resolveSession(videoId, request.getSessionId(), request.getMessage());
        chatMemoryStore.appendMessage(session.id(), "user", request.getMessage());

        List<ChatMessageResponse> previous = chatMemoryStore.listMessagesBeforeLast(session.id());
        List<Map<String, String>> history = new ArrayList<>();
        for (ChatMessageResponse msg : previous) {
            if ("user".equals(msg.getRole()) || "assistant".equals(msg.getRole())) {
                history.add(Map.of("role", msg.getRole(), "content", msg.getContent()));
            }
        }

        String summaryText = summaryRepository.findByVideoId(videoId).map(Summary::getContent).orElse("");
        boolean summaryStyle = llmService.isSummaryStyleQuestion(request.getMessage());
        List<TranscriptIndexService.RetrievedChunk> chunks = summaryStyle
                ? transcriptIndexService.search(videoId, "核心内容 要点 主题", 10)
                : transcriptIndexService.search(videoId, request.getMessage(), 5);
        String transcriptExcerpt = summaryStyle ? transcript.getFullText() : null;

        String reply = llmService.chatWithRagContext(
                video.getTitle(),
                summaryText,
                transcriptExcerpt,
                chunks,
                history,
                request.getMessage()
        );

        chatMemoryStore.appendMessage(session.id(), "assistant", reply);

        List<CitationResponse> citations = summaryStyle ? List.of() : chunks.stream()
                .map(c -> CitationResponse.builder()
                        .startSec(c.startSec())
                        .endSec(c.endSec())
                        .quote(abbreviate(c.text(), 120))
                        .build())
                .toList();

        return ChatResponse.builder()
                .sessionId(session.id())
                .reply(reply)
                .citations(citations)
                .history(chatMemoryStore.listMessages(session.id()))
                .build();
    }

    public List<ChatMessageResponse> getSessionMessages(Long sessionId) {
        ChatSessionRecord session = chatMemoryStore.findSession(sessionId)
                .orElseThrow(() -> new BusinessException("会话不存在"));
        assertSessionOwned(session);
        getReadyVideo(session.videoId());
        return chatMemoryStore.listMessages(sessionId);
    }

    public ChatHistoryResponse getLatestChatHistory(Long videoId) {
        getReadyVideo(videoId);
        Long userId = currentUserService.requireUserId();
        return chatMemoryStore.findLatestSessionId(userId, videoId)
                .flatMap(chatMemoryStore::findSession)
                .map(session -> ChatHistoryResponse.builder()
                        .sessionId(session.id())
                        .history(chatMemoryStore.listMessages(session.id()))
                        .build())
                .orElse(ChatHistoryResponse.builder().sessionId(null).history(List.of()).build());
    }

    private ChatSessionRecord resolveSession(Long videoId, Long sessionId, String firstMessage) {
        if (sessionId != null) {
            ChatSessionRecord session = chatMemoryStore.findSession(sessionId)
                    .orElseThrow(() -> new BusinessException("会话不存在"));
            if (!session.videoId().equals(videoId)) {
                throw new BusinessException("会话与视频不匹配");
            }
            assertSessionOwned(session);
            return session;
        }

        String title = firstMessage.length() > 30 ? firstMessage.substring(0, 30) + "..." : firstMessage;
        return chatMemoryStore.createSession(videoId, currentUserService.requireUserId(), title);
    }

    private Video getReadyVideo(Long videoId) {
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new BusinessException("视频不存在"));
        if (!video.getUserId().equals(currentUserService.requireUserId())) {
            throw new BusinessException("无权访问该视频");
        }
        if (video.getStatus() != VideoStatus.READY) {
            throw new BusinessException("视频尚未处理完成，请稍后再试");
        }
        return video;
    }

    private void assertSessionOwned(ChatSessionRecord session) {
        if (!session.userId().equals(currentUserService.requireUserId())) {
            throw new BusinessException("无权访问该会话");
        }
    }

    private String abbreviate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }
}
