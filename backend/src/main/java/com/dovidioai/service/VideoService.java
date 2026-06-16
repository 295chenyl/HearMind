package com.dovidioai.service;

import com.dovidioai.domain.entity.Summary;
import com.dovidioai.domain.entity.Transcript;
import com.dovidioai.domain.entity.Video;
import com.dovidioai.domain.enums.ContentType;
import com.dovidioai.domain.enums.SourceType;
import com.dovidioai.domain.enums.VideoStatus;
import com.dovidioai.domain.repository.SummaryRepository;
import com.dovidioai.domain.repository.TranscriptRepository;
import com.dovidioai.domain.repository.VideoRepository;
import com.dovidioai.exception.BusinessException;
import com.dovidioai.service.dashscope.DashScopeLlmService;
import com.dovidioai.config.DashScopeProperties;
import com.dovidioai.service.importing.ImportCookieService;
import com.dovidioai.service.importing.YtDlpService;
import com.dovidioai.service.storage.VideoStorageFacade;
import com.dovidioai.support.TranscriptSegmentParser;
import com.dovidioai.redis.RedisChatMemoryStore;
import com.dovidioai.web.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class VideoService {

    private final VideoRepository videoRepository;
    private final TranscriptRepository transcriptRepository;
    private final SummaryRepository summaryRepository;
    private final VideoStorageFacade storageFacade;
    private final VideoProcessingService processingService;
    private final YtDlpService ytDlpService;
    private final ImportCookieService importCookieService;
    private final CurrentUserService currentUserService;
    private final TranscriptSegmentParser segmentParser;
    private final DashScopeLlmService llmService;
    private final DashScopeProperties dashScopeProperties;
    private final TranscriptIndexService transcriptIndexService;
    private final RedisChatMemoryStore chatMemoryStore;

    @Transactional
    public VideoResponse upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的文件");
        }

        String displayTitle = sanitizeFilename(file.getOriginalFilename());
        Video video = createBaseVideo(SourceType.UPLOAD, null, displayTitle, ContentType.GENERAL);
        videoRepository.save(video);

        String storedFilename = buildStoredFilename(file.getOriginalFilename());
        Path saved = storageFacade.saveUploadedFile(video.getId(), file, storedFilename);
        String hash = storageFacade.computeSha256(saved);
        video.setContentHash(hash);
        video.setDedupKey("hash:" + hash);

        Optional<Video> duplicate = findReadyDuplicate(currentUserService.requireUserId(), video.getDedupKey());
        if (duplicate.isPresent()) {
            storageFacade.deleteVideoDir(video.getId());
            videoRepository.delete(video);
            return toResponse(duplicate.get(), true);
        }

        video.setFilePath(storageFacade.storeReference(video.getId(), saved, storedFilename));
        videoRepository.save(video);
        scheduleProcessing(video.getId());
        return toResponse(video, false);
    }

    public ImportUrlPreviewResponse previewImportUrl(String url, MultipartFile cookieFile, String cookieText) {
        String trimmed = url.trim();
        Long userId = currentUserService.requireUserId();
        Path cookiePath = importCookieService.saveTempCookie(userId, cookieFile, cookieText);
        try {
            if (ytDlpService.isDirectHttpUrl(trimmed)) {
                return ImportUrlPreviewResponse.builder()
                        .title(extractNameFromUrl(trimmed))
                        .platform("direct")
                        .warnings(buildCookieWarnings(trimmed, cookiePath))
                        .build();
            }
            YtDlpService.MetadataResult meta = ytDlpService.probeMetadata(trimmed, cookiePath);
            return ImportUrlPreviewResponse.builder()
                    .title(meta.title())
                    .durationSec(meta.durationSec())
                    .platform(meta.platform())
                    .platformVideoId(meta.platformVideoId())
                    .warnings(buildCookieWarnings(trimmed, cookiePath))
                    .build();
        } finally {
            importCookieService.deleteIfExists(cookiePath != null ? cookiePath.toString() : null);
        }
    }

    @Transactional
    public VideoResponse importUrl(String url, MultipartFile cookieFile, String cookieText, ContentType contentType) {
        String trimmed = url.trim();
        Long userId = currentUserService.requireUserId();
        Path cookiePath = importCookieService.saveTempCookie(userId, cookieFile, cookieText);

        String platform = ytDlpService.detectPlatform(trimmed);
        String platformVideoId = ytDlpService.extractPlatformVideoId(trimmed);
        String dedupKey = platformVideoId != null && !platformVideoId.isBlank()
                ? platform + ":" + platformVideoId
                : "url:" + trimmed.hashCode();

        Optional<Video> duplicate = findReadyDuplicate(userId, dedupKey);
        if (duplicate.isPresent()) {
            importCookieService.deleteIfExists(cookiePath != null ? cookiePath.toString() : null);
            return toResponse(duplicate.get(), true);
        }

        Video video = createBaseVideo(SourceType.URL, trimmed, "链接导入视频", contentType);
        video.setPlatform(platform);
        video.setPlatformVideoId(platformVideoId);
        video.setDedupKey(dedupKey);
        if (cookiePath != null) {
            video.setImportCookiePath(cookiePath.toAbsolutePath().toString());
        }
        videoRepository.save(video);
        scheduleProcessing(video.getId());
        return toResponse(video, false);
    }

    @Transactional
    public VideoResponse retry(Long id) {
        Video video = getOwnedVideo(id);
        if (video.getStatus() == VideoStatus.READY) {
            throw new BusinessException("视频已处理完成，无需重试");
        }
        video.setStatus(VideoStatus.PENDING);
        video.setErrorMessage(null);
        videoRepository.save(video);
        scheduleProcessing(id);
        return toResponse(video, false);
    }

    public List<VideoResponse> listVideos() {
        return videoRepository.findByUserIdOrderByCreatedAtDesc(currentUserService.requireUserId())
                .stream()
                .map(v -> toResponse(v, false))
                .toList();
    }

    public VideoResponse getVideo(Long id) {
        return toResponse(getOwnedVideo(id), false);
    }

    public TranscriptResponse getTranscript(Long id) {
        getOwnedVideo(id);
        Transcript transcript = transcriptRepository.findByVideoId(id)
                .orElseThrow(() -> new BusinessException("转写尚未完成"));

        List<TranscriptSegmentResponse> segments = segmentParser.parse(transcript.getSegmentsJson()).stream()
                .map(s -> TranscriptSegmentResponse.builder()
                        .startMs(s.startMs())
                        .endMs(s.endMs())
                        .text(s.text())
                        .build())
                .toList();

        return TranscriptResponse.builder()
                .videoId(id)
                .fullText(transcript.getFullText())
                .segmentsJson(transcript.getSegmentsJson())
                .segments(segments)
                .createdAt(transcript.getCreatedAt())
                .build();
    }

    public SummaryResponse getSummary(Long id) {
        getOwnedVideo(id);
        Summary summary = summaryRepository.findByVideoId(id)
                .orElseThrow(() -> new BusinessException("摘要尚未生成"));
        return toSummaryResponse(id, summary);
    }

    @Transactional
    public SummaryResponse updateSummary(Long id, UpdateSummaryRequest request) {
        getOwnedVideo(id);
        Summary summary = summaryRepository.findByVideoId(id)
                .orElseThrow(() -> new BusinessException("摘要尚未生成"));
        summary.setContent(request.getContent().trim());
        summary.setUserEdited(true);
        summaryRepository.save(summary);
        return toSummaryResponse(id, summary);
    }

    @Transactional
    public SummaryResponse regenerateSummary(Long id) {
        Video video = getOwnedVideo(id);
        Transcript transcript = transcriptRepository.findByVideoId(id)
                .orElseThrow(() -> new BusinessException("转写尚未完成"));
        String summaryText = llmService.generateSummary(
                video.getTitle(),
                transcript.getFullText(),
                video.getContentType() != null ? video.getContentType() : ContentType.GENERAL
        );
        Summary summary = summaryRepository.findByVideoId(id).orElse(new Summary());
        summary.setVideoId(id);
        summary.setContent(summaryText);
        summary.setModel(dashScopeProperties.getLlmModel());
        summary.setUserEdited(false);
        summaryRepository.save(summary);
        return toSummaryResponse(id, summary);
    }

    @Transactional
    public VideoResponse updateContentType(Long id, ContentType contentType) {
        Video video = getOwnedVideo(id);
        video.setContentType(contentType != null ? contentType : ContentType.GENERAL);
        videoRepository.save(video);
        return toResponse(video, false);
    }

    public void rebuildIndex(Long id) {
        getOwnedVideo(id);
        transcriptIndexService.buildIndex(id);
    }

    public String exportMarkdown(Long id) {
        Video video = getOwnedVideo(id);
        if (video.getStatus() != VideoStatus.READY) {
            throw new BusinessException("视频尚未处理完成");
        }
        Transcript transcript = transcriptRepository.findByVideoId(id)
                .orElseThrow(() -> new BusinessException("转写尚未完成"));
        Summary summary = summaryRepository.findByVideoId(id).orElse(null);

        StringBuilder md = new StringBuilder();
        md.append("# ").append(video.getTitle()).append("\n\n");
        if (video.getSourceUrl() != null && !video.getSourceUrl().isBlank()) {
            md.append("- 来源：").append(video.getSourceUrl()).append("\n");
        }
        if (video.getDurationSec() != null) {
            md.append("- 时长：").append(formatDuration(video.getDurationSec())).append("\n");
        }
        md.append("\n## 摘要\n\n");
        md.append(summary != null ? summary.getContent() : "（暂无摘要）").append("\n\n");
        md.append("## 转写\n\n");
        List<TranscriptSegmentParser.Segment> segments = segmentParser.parse(transcript.getSegmentsJson());
        for (TranscriptSegmentParser.Segment segment : segments) {
            md.append("- **").append(formatTime((int) (segment.startMs() / 1000))).append("** ")
                    .append(segment.text()).append("\n");
        }
        if (segments.isEmpty()) {
            md.append(transcript.getFullText()).append("\n");
        }
        appendChatHistory(md, video);
        return md.toString();
    }

    private void appendChatHistory(StringBuilder md, Video video) {
        Long userId = currentUserService.requireUserId();
        chatMemoryStore.findLatestSessionId(userId, video.getId())
                .flatMap(chatMemoryStore::findSession)
                .ifPresent(session -> {
                    List<ChatMessageResponse> messages = chatMemoryStore.listMessages(session.id());
                    if (messages.isEmpty()) {
                        return;
                    }
                    md.append("\n## 问答记录\n\n");
                    for (ChatMessageResponse msg : messages) {
                        String role = "user".equals(msg.getRole()) ? "问" : "答";
                        md.append("**").append(role).append("**：").append(msg.getContent()).append("\n\n");
                    }
                });
    }

    public Path getVideoFilePath(Long id) {
        Video video = getOwnedVideo(id);
        if (video.getFilePath() == null) {
            throw new BusinessException("视频文件不存在");
        }
        return storageFacade.getLocalStreamPath(video.getFilePath());
    }

    public String getVideoStreamRedirectUrl(Long id) {
        Video video = getOwnedVideo(id);
        if (video.getFilePath() == null) {
            throw new BusinessException("视频文件不存在");
        }
        return storageFacade.getSignedStreamUrl(video.getFilePath());
    }

    private Optional<Video> findReadyDuplicate(Long userId, String dedupKey) {
        if (dedupKey == null || dedupKey.isBlank()) {
            return Optional.empty();
        }
        return videoRepository.findByUserIdAndDedupKeyAndStatus(userId, dedupKey, VideoStatus.READY);
    }

    private Video createBaseVideo(SourceType sourceType, String sourceUrl, String title, ContentType contentType) {
        Video video = new Video();
        video.setUserId(currentUserService.requireUserId());
        video.setTitle(title != null && !title.isBlank() ? title : "未命名视频");
        video.setSourceType(sourceType);
        video.setSourceUrl(sourceUrl);
        video.setStatus(VideoStatus.PENDING);
        video.setContentType(contentType != null ? contentType : ContentType.GENERAL);
        return video;
    }

    private Video getOwnedVideo(Long id) {
        Video video = videoRepository.findById(id)
                .orElseThrow(() -> new BusinessException("视频不存在"));
        if (!video.getUserId().equals(currentUserService.requireUserId())) {
            throw new BusinessException("无权访问该视频");
        }
        return video;
    }

    private VideoResponse toResponse(Video video, boolean deduplicated) {
        return VideoResponse.builder()
                .id(video.getId())
                .title(video.getTitle())
                .sourceType(video.getSourceType())
                .sourceUrl(video.getSourceUrl())
                .platform(video.getPlatform())
                .contentType(video.getContentType())
                .durationSec(video.getDurationSec())
                .fileSize(video.getFileSize())
                .status(video.getStatus())
                .errorMessage(video.getErrorMessage())
                .createdAt(video.getCreatedAt())
                .updatedAt(video.getUpdatedAt())
                .deduplicated(deduplicated)
                .build();
    }

    private SummaryResponse toSummaryResponse(Long videoId, Summary summary) {
        return SummaryResponse.builder()
                .videoId(videoId)
                .content(summary.getContent())
                .model(summary.getModel())
                .userEdited(summary.isUserEdited())
                .createdAt(summary.getCreatedAt())
                .updatedAt(summary.getUpdatedAt())
                .build();
    }

    private void scheduleProcessing(Long videoId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                processingService.processAsync(videoId);
            }
        });
    }

    private List<String> buildCookieWarnings(String url, Path cookiePath) {
        List<String> warnings = new ArrayList<>();
        if (url.toLowerCase().contains("bilibili.com")
                && cookiePath == null
                && !ytDlpService.isGlobalCookieConfigured()) {
            warnings.add("未检测到 B 站 Cookie，部分视频可能导入失败，请参考 docs/COOKIE.md");
        }
        return warnings;
    }

    private String buildStoredFilename(String originalName) {
        String ext = ".mp4";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf('.')).toLowerCase();
            if (ext.length() > 10 || ext.contains(" ")) {
                ext = ".mp4";
            }
        }
        return "source" + ext;
    }

    private String sanitizeFilename(String name) {
        if (name == null || name.isBlank()) {
            return "upload.bin";
        }
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private String extractNameFromUrl(String url) {
        String path = url;
        int q = path.indexOf('?');
        if (q > 0) {
            path = path.substring(0, q);
        }
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private String formatDuration(int seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    private String formatTime(int totalSec) {
        return String.format("%02d:%02d", totalSec / 60, totalSec % 60);
    }
}
