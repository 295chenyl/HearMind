package com.dovidioai.service;

import com.dovidioai.config.AppProperties;
import com.dovidioai.config.DashScopeProperties;
import com.dovidioai.domain.entity.Summary;
import com.dovidioai.domain.entity.Transcript;
import com.dovidioai.domain.entity.Video;
import com.dovidioai.domain.enums.SourceType;
import com.dovidioai.domain.enums.VideoStatus;
import com.dovidioai.domain.repository.SummaryRepository;
import com.dovidioai.domain.repository.TranscriptRepository;
import com.dovidioai.domain.repository.VideoRepository;
import com.dovidioai.exception.BusinessException;
import com.dovidioai.service.dashscope.DashScopeAsrService;
import com.dovidioai.service.dashscope.DashScopeLlmService;
import com.dovidioai.service.importing.DirectDownloadService;
import com.dovidioai.service.importing.ImportCookieService;
import com.dovidioai.service.importing.YtDlpService;
import com.dovidioai.service.media.MediaProcessService;
import com.dovidioai.service.storage.VideoStorageFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoProcessingService {

    private final VideoRepository videoRepository;
    private final TranscriptRepository transcriptRepository;
    private final SummaryRepository summaryRepository;
    private final VideoStorageFacade storageFacade;
    private final MediaProcessService mediaProcessService;
    private final YtDlpService ytDlpService;
    private final DirectDownloadService directDownloadService;
    private final DashScopeAsrService asrService;
    private final DashScopeLlmService llmService;
    private final TranscriptIndexService transcriptIndexService;
    private final ImportCookieService importCookieService;
    private final AppProperties appProperties;
    private final DashScopeProperties dashScopeProperties;

    @Async("videoTaskExecutor")
    public void processAsync(Long videoId) {
        try {
            log.info("开始处理视频 videoId={}", videoId);
            process(videoId);
            log.info("视频处理完成 videoId={}", videoId);
        } catch (Throwable e) {
            log.error("视频处理失败 videoId={}", videoId, e);
            failVideo(videoId, e.getMessage());
        }
    }

    public void process(Long videoId) {
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new BusinessException("视频不存在"));
        Path cookiePath = resolveCookiePath(video);

        try {
            markStatus(videoId, VideoStatus.DOWNLOADING);

            video = videoRepository.findById(videoId).orElseThrow();
            Path mediaPath = resolveMediaPath(video, cookiePath);
            mediaProcessService.validateDuration(mediaPath);

            double duration = mediaProcessService.probeDurationSeconds(mediaPath);
            video.setDurationSec((int) Math.round(duration));
            video.setFileSize(Files.size(mediaPath));
            if (video.getSourceType() == SourceType.UPLOAD && video.getFilePath() != null) {
                // 保持已有引用
            } else {
                String filename = mediaPath.getFileName().toString();
                video.setFilePath(storageFacade.storeReference(videoId, mediaPath, filename));
            }
            videoRepository.save(video);

            Path audioPath = mediaProcessService.extractAudioFromMedia(
                    mediaPath,
                    storageFacade.getVideoDir(videoId).resolve("audio.wav")
            );
            video.setAudioPath(audioPath.toAbsolutePath().toString());
            videoRepository.save(video);
            markStatus(videoId, VideoStatus.TRANSCRIBING);

            DashScopeAsrService.TranscriptionResult transcription = asrService.transcribe(audioPath);
            Transcript transcript = transcriptRepository.findByVideoId(videoId).orElse(new Transcript());
            transcript.setVideoId(videoId);
            transcript.setFullText(transcription.fullText());
            transcript.setSegmentsJson(transcription.segmentsJson());
            transcriptRepository.save(transcript);

            markStatus(videoId, VideoStatus.SUMMARIZING);
            video = videoRepository.findById(videoId).orElseThrow();
            String summaryText = llmService.generateSummary(
                    video.getTitle(),
                    transcription.fullText(),
                    video.getContentType()
            );

            Summary summary = summaryRepository.findByVideoId(videoId).orElse(new Summary());
            summary.setVideoId(videoId);
            summary.setContent(summaryText);
            summary.setModel(dashScopeProperties.getLlmModel());
            summary.setUserEdited(false);
            summaryRepository.save(summary);

            try {
                transcriptIndexService.buildIndex(videoId);
            } catch (Exception indexEx) {
                log.warn("RAG 索引构建失败 videoId={}，视频仍标记为 READY: {}", videoId, indexEx.getMessage());
            }
            markStatus(videoId, VideoStatus.READY);
        } catch (Exception e) {
            log.error("处理流水线失败 videoId={}", videoId, e);
            failVideo(videoId, e.getMessage());
        } finally {
            cleanupImportCookie(video, cookiePath);
        }
    }

    private Path resolveCookiePath(Video video) {
        if (video.getImportCookiePath() == null || video.getImportCookiePath().isBlank()) {
            return null;
        }
        Path path = Path.of(video.getImportCookiePath());
        return Files.isRegularFile(path) ? path : null;
    }

    private void cleanupImportCookie(Video video, Path cookiePath) {
        if (cookiePath != null) {
            importCookieService.deleteIfExists(cookiePath.toString());
        }
        if (video.getImportCookiePath() != null) {
            videoRepository.findById(video.getId()).ifPresent(v -> {
                v.setImportCookiePath(null);
                videoRepository.save(v);
            });
        }
    }

    private void markStatus(Long videoId, VideoStatus status) {
        videoRepository.findById(videoId).ifPresent(video -> {
            video.setStatus(status);
            video.setErrorMessage(null);
            videoRepository.save(video);
        });
    }

    private Path resolveMediaPath(Video video, Path cookiePath) {
        if (video.getSourceType() == SourceType.UPLOAD) {
            if (video.getFilePath() == null) {
                throw new BusinessException("上传文件路径为空");
            }
            return storageFacade.resolveLocalPath(
                    video.getFilePath(),
                    video.getId(),
                    "source.mp4"
            );
        }

        updateStatus(video, VideoStatus.DOWNLOADING);
        Path dir = storageFacade.getVideoDir(video.getId());
        if (video.getSourceUrl() != null && ytDlpService.isDirectHttpUrl(video.getSourceUrl())) {
            String filename = resolveDirectFilename(video.getSourceUrl());
            Path target = dir.resolve(filename);
            directDownloadService.download(video.getSourceUrl(), target);
            video.setPlatform("direct");
            video.setTitle(extractNameFromUrl(video.getSourceUrl()));
            videoRepository.save(video);
            return target;
        }

        YtDlpService.DownloadResult result = ytDlpService.download(
                video.getSourceUrl(),
                dir,
                video.getTitle(),
                video.getPlatform(),
                video.getPlatformVideoId(),
                cookiePath
        );
        video.setTitle(result.title());
        video.setPlatform(result.platform());
        video.setPlatformVideoId(result.platformVideoId());
        video.setDedupKey(buildUrlDedupKey(result.platform(), result.platformVideoId()));
        videoRepository.save(video);
        return result.filePath();
    }

    private String resolveDirectFilename(String url) {
        String path = url;
        int q = path.indexOf('?');
        if (q > 0) {
            path = path.substring(0, q);
        }
        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : "source.bin";
        if (!name.contains(".")) {
            name = "source.bin";
        }
        if (name.length() > 200) {
            name = name.substring(name.length() - 200);
        }
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private void updateStatus(Video video, VideoStatus status) {
        video.setStatus(status);
        video.setErrorMessage(null);
        videoRepository.save(video);
    }

    @Transactional
    public void failVideo(Long videoId, String message) {
        videoRepository.findById(videoId).ifPresent(video -> {
            video.setStatus(VideoStatus.FAILED);
            video.setErrorMessage(message != null ? message.substring(0, Math.min(message.length(), 2000)) : "未知错误");
            videoRepository.save(video);
            if (video.getImportCookiePath() != null) {
                importCookieService.deleteIfExists(video.getImportCookiePath());
                video.setImportCookiePath(null);
                videoRepository.save(video);
            }
        });
    }

    private String buildUrlDedupKey(String platform, String platformVideoId) {
        if (platformVideoId == null || platformVideoId.isBlank()) {
            return null;
        }
        return platform + ":" + platformVideoId;
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
}
