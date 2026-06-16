package com.dovidioai.service.storage;

import com.dovidioai.config.OssProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

@Service
@RequiredArgsConstructor
public class VideoStorageFacade {

    private final LocalStorageService localStorageService;
    private final OssProperties ossProperties;

    public Path getVideoDir(Long videoId) {
        return localStorageService.getVideoDir(videoId);
    }

    public Path getChunkDir(String sessionId) {
        return localStorageService.getChunkDir(sessionId);
    }

    public void cleanupChunkDir(String sessionId) {
        localStorageService.cleanupChunkDir(sessionId);
    }

    public void deleteVideoDir(Long videoId) {
        localStorageService.deleteVideoDir(videoId);
    }

    public Path saveUploadedFile(Long videoId, MultipartFile file, String storedFilename) {
        return localStorageService.saveUploadedFile(videoId, file, storedFilename);
    }

    public void saveChunk(String sessionId, int chunkIndex, MultipartFile chunk) {
        localStorageService.saveChunk(sessionId, chunkIndex, chunk);
    }

    public Path mergeChunks(String sessionId, int totalChunks, String storedFilename) {
        return localStorageService.mergeChunks(sessionId, totalChunks, storedFilename);
    }

    public String storeReference(Long videoId, Path localPath, String filename) {
        return localStorageService.storeReference(videoId, localPath, filename);
    }

    public Path resolveLocalPath(String filePath, Long videoId, String fallbackFilename) {
        return localStorageService.resolveLocalPath(filePath, videoId, fallbackFilename);
    }

    public Path getLocalStreamPath(String filePath) {
        return localStorageService.getLocalStreamPath(filePath);
    }

    public String getSignedStreamUrl(String filePath) {
        if (!ossProperties.isEnabled()) {
            return null;
        }
        return null;
    }

    public String computeSha256(Path path) {
        return localStorageService.computeSha256(path);
    }

    public Path tempCookiesDir(Long userId) {
        return localStorageService.tempCookiesDir(userId);
    }
}
