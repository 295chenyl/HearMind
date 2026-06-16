package com.dovidioai.service;

import com.dovidioai.domain.entity.UploadSession;
import com.dovidioai.domain.entity.Video;
import com.dovidioai.domain.enums.SourceType;
import com.dovidioai.domain.enums.VideoStatus;
import com.dovidioai.domain.repository.UploadSessionRepository;
import com.dovidioai.domain.repository.VideoRepository;
import com.dovidioai.exception.BusinessException;
import com.dovidioai.service.storage.VideoStorageFacade;
import com.dovidioai.web.dto.UploadInitRequest;
import com.dovidioai.web.dto.UploadSessionResponse;
import com.dovidioai.web.dto.VideoResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ResumableUploadService {

    private static final String STATUS_UPLOADING = "UPLOADING";
    private static final String STATUS_COMPLETED = "COMPLETED";

    private final UploadSessionRepository uploadSessionRepository;
    private final VideoRepository videoRepository;
    private final VideoStorageFacade storageFacade;
    private final VideoProcessingService processingService;
    private final CurrentUserService currentUserService;
    private final ObjectMapper objectMapper;

    @Transactional
    public UploadSessionResponse init(UploadInitRequest request) {
        Long userId = currentUserService.requireUserId();
        if (request.getContentHash() != null && !request.getContentHash().isBlank()) {
            Optional<Video> dup = findReadyDuplicate(userId, "hash:" + request.getContentHash().trim());
            if (dup.isPresent()) {
                return UploadSessionResponse.builder()
                        .status("DEDUP")
                        .videoId(dup.get().getId())
                        .build();
            }
        }

        String filename = sanitizeFilename(request.getFilename());
        Optional<UploadSession> resumable = uploadSessionRepository
                .findFirstByUserIdAndStatusAndFilenameAndFileSizeAndExpiresAtAfterOrderByUpdatedAtDesc(
                        userId, STATUS_UPLOADING, filename, request.getFileSize(), LocalDateTime.now());
        if (resumable.isPresent()) {
            UploadSession existing = resumable.get();
            if (request.getContentHash() == null || request.getContentHash().isBlank()
                    || existing.getContentHash() == null || existing.getContentHash().isBlank()
                    || request.getContentHash().trim().equals(existing.getContentHash())) {
                return toResponse(existing);
            }
        }

        int totalChunks = (int) Math.ceil(request.getFileSize() * 1.0 / request.getChunkSize());
        UploadSession session = new UploadSession();
        session.setId(UUID.randomUUID().toString().replace("-", ""));
        session.setUserId(userId);
        session.setFilename(filename);
        session.setFileSize(request.getFileSize());
        session.setChunkSize(request.getChunkSize());
        session.setTotalChunks(totalChunks);
        session.setUploadedChunksJson("[]");
        session.setContentHash(request.getContentHash());
        session.setStatus(STATUS_UPLOADING);
        session.setExpiresAt(LocalDateTime.now().plusDays(2));
        uploadSessionRepository.save(session);
        storageFacade.getChunkDir(session.getId());
        return toResponse(session);
    }

    @Transactional
    public UploadSessionResponse uploadChunk(String sessionId, int chunkIndex, MultipartFile chunk) {
        UploadSession session = getOwnedSession(sessionId);
        if (!STATUS_UPLOADING.equals(session.getStatus())) {
            throw new BusinessException("上传会话已结束");
        }
        if (chunkIndex < 0 || chunkIndex >= session.getTotalChunks()) {
            throw new BusinessException("分片序号无效");
        }
        storageFacade.saveChunk(sessionId, chunkIndex, chunk);
        List<Integer> uploaded = readUploadedChunks(session);
        uploaded.add(chunkIndex);
        TreeSet<Integer> unique = new TreeSet<>(uploaded);
        session.setUploadedChunksJson(writeUploadedChunks(new ArrayList<>(unique)));
        uploadSessionRepository.save(session);
        return toResponse(session);
    }

    public UploadSessionResponse getStatus(String sessionId) {
        return toResponse(getOwnedSession(sessionId));
    }

    public List<UploadSessionResponse> listResumable() {
        Long userId = currentUserService.requireUserId();
        return uploadSessionRepository.findByUserIdAndStatusOrderByUpdatedAtDesc(userId, STATUS_UPLOADING)
                .stream()
                .filter(s -> s.getExpiresAt().isAfter(LocalDateTime.now()))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public VideoResponse complete(String sessionId) {
        UploadSession session = getOwnedSession(sessionId);
        if (!STATUS_UPLOADING.equals(session.getStatus())) {
            throw new BusinessException("上传会话已结束");
        }
        List<Integer> uploaded = readUploadedChunks(session);
        if (uploaded.size() != session.getTotalChunks()) {
            throw new BusinessException("分片未全部上传，已完成 " + uploaded.size() + "/" + session.getTotalChunks());
        }

        String storedFilename = buildStoredFilename(session.getFilename());
        Path merged = storageFacade.mergeChunks(sessionId, session.getTotalChunks(), storedFilename);

        Video video = new Video();
        video.setUserId(session.getUserId());
        video.setTitle(session.getFilename());
        video.setSourceType(SourceType.UPLOAD);
        video.setStatus(VideoStatus.PENDING);
        videoRepository.save(video);

        Path target = storageFacade.getVideoDir(video.getId()).resolve(storedFilename);
        try {
            Files.move(merged, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            throw new BusinessException("保存合并文件失败: " + e.getMessage());
        }

        String hash = storageFacade.computeSha256(target);
        video.setContentHash(hash);
        video.setDedupKey("hash:" + hash);
        video.setFileSize(session.getFileSize());
        video.setFilePath(storageFacade.storeReference(video.getId(), target, storedFilename));
        videoRepository.save(video);

        Optional<Video> dup = findReadyDuplicate(session.getUserId(), video.getDedupKey());
        if (dup.isPresent() && !dup.get().getId().equals(video.getId())) {
            storageFacade.deleteVideoDir(video.getId());
            videoRepository.delete(video);
            storageFacade.cleanupChunkDir(sessionId);
            markSessionCompleted(session, dup.get().getId());
            return VideoResponse.builder()
                    .id(dup.get().getId())
                    .title(dup.get().getTitle())
                    .sourceType(dup.get().getSourceType())
                    .status(dup.get().getStatus())
                    .deduplicated(true)
                    .build();
        }

        scheduleProcessing(video.getId());
        storageFacade.cleanupChunkDir(sessionId);
        markSessionCompleted(session, video.getId());

        return VideoResponse.builder()
                .id(video.getId())
                .title(video.getTitle())
                .sourceType(video.getSourceType())
                .status(video.getStatus())
                .deduplicated(false)
                .build();
    }

    private void markSessionCompleted(UploadSession session, Long videoId) {
        session.setStatus(STATUS_COMPLETED);
        session.setVideoId(videoId);
        uploadSessionRepository.save(session);
    }

    private UploadSession getOwnedSession(String sessionId) {
        Long userId = currentUserService.requireUserId();
        UploadSession session = uploadSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new BusinessException("上传会话不存在"));
        if (session.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("上传会话已过期");
        }
        return session;
    }

    private Optional<Video> findReadyDuplicate(Long userId, String dedupKey) {
        if (dedupKey == null || dedupKey.isBlank()) {
            return Optional.empty();
        }
        return videoRepository.findByUserIdAndDedupKeyAndStatus(userId, dedupKey, VideoStatus.READY);
    }

    private void scheduleProcessing(Long videoId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                processingService.processAsync(videoId);
            }
        });
    }

    private UploadSessionResponse toResponse(UploadSession session) {
        return UploadSessionResponse.builder()
                .sessionId(session.getId())
                .filename(session.getFilename())
                .fileSize(session.getFileSize())
                .chunkSize(session.getChunkSize())
                .totalChunks(session.getTotalChunks())
                .uploadedChunks(readUploadedChunks(session))
                .status(session.getStatus())
                .videoId(session.getVideoId())
                .expiresAt(session.getExpiresAt())
                .build();
    }

    private List<Integer> readUploadedChunks(UploadSession session) {
        try {
            return objectMapper.readValue(session.getUploadedChunksJson(), new TypeReference<>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private String writeUploadedChunks(List<Integer> chunks) {
        try {
            return objectMapper.writeValueAsString(chunks);
        } catch (Exception e) {
            throw new BusinessException("记录分片状态失败");
        }
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
}
