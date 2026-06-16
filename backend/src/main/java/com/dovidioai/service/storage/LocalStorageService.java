package com.dovidioai.service.storage;

import com.dovidioai.config.AppProperties;
import com.dovidioai.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class LocalStorageService {

    private final AppProperties appProperties;

    public Path storageRoot() {
        return Path.of(appProperties.getStoragePath()).toAbsolutePath().normalize();
    }

    public Path getVideoDir(Long videoId) {
        Path dir = storageRoot().resolve("videos").resolve(String.valueOf(videoId));
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new BusinessException("创建视频目录失败: " + e.getMessage());
        }
        return dir;
    }

    public Path getChunkDir(String sessionId) {
        Path dir = storageRoot().resolve("chunks").resolve(sessionId);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new BusinessException("创建分片目录失败: " + e.getMessage());
        }
        return dir;
    }

    public void cleanupChunkDir(String sessionId) {
        deleteRecursively(storageRoot().resolve("chunks").resolve(sessionId));
    }

    public void deleteVideoDir(Long videoId) {
        deleteRecursively(getVideoDir(videoId));
    }

    public Path saveUploadedFile(Long videoId, MultipartFile file, String storedFilename) {
        Path target = getVideoDir(videoId).resolve(storedFilename);
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BusinessException("保存上传文件失败: " + e.getMessage());
        }
        return target;
    }

    public void saveChunk(String sessionId, int chunkIndex, MultipartFile chunk) {
        Path target = getChunkDir(sessionId).resolve("chunk-" + chunkIndex);
        try (InputStream in = chunk.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BusinessException("保存分片失败: " + e.getMessage());
        }
    }

    public Path mergeChunks(String sessionId, int totalChunks, String storedFilename) {
        Path chunkDir = getChunkDir(sessionId);
        Path merged = chunkDir.resolve("merged-" + storedFilename);
        try {
            Files.deleteIfExists(merged);
            try (var out = Files.newOutputStream(merged, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                IntStream.range(0, totalChunks).forEach(i -> {
                    Path part = chunkDir.resolve("chunk-" + i);
                    if (!Files.isRegularFile(part)) {
                        throw new BusinessException("分片 " + i + " 不存在");
                    }
                    try {
                        Files.copy(part, out);
                    } catch (IOException e) {
                        throw new BusinessException("合并分片失败: " + e.getMessage());
                    }
                });
            }
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            throw new BusinessException("合并分片失败: " + e.getMessage());
        }
        return merged;
    }

    public String storeReference(Long videoId, Path localPath, String filename) {
        Path videoDir = getVideoDir(videoId);
        Path normalized = localPath.toAbsolutePath().normalize();
        if (normalized.startsWith(videoDir)) {
            return "local:" + videoId + "/" + normalized.getFileName();
        }
        return normalized.toString();
    }

    public Path resolveLocalPath(String filePath, Long videoId, String fallbackFilename) {
        if (filePath == null || filePath.isBlank()) {
            return getVideoDir(videoId).resolve(fallbackFilename);
        }
        if (filePath.startsWith("local:")) {
            String suffix = filePath.substring("local:".length());
            int slash = suffix.indexOf('/');
            if (slash > 0) {
                String name = suffix.substring(slash + 1);
                return getVideoDir(videoId).resolve(name);
            }
        }
        Path path = Path.of(filePath);
        if (path.isAbsolute() && Files.isRegularFile(path)) {
            return path;
        }
        Path inVideoDir = getVideoDir(videoId).resolve(path.getFileName().toString());
        if (Files.isRegularFile(inVideoDir)) {
            return inVideoDir;
        }
        return path.toAbsolutePath().normalize();
    }

    public Path getLocalStreamPath(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            throw new BusinessException("视频文件路径为空");
        }
        if (filePath.startsWith("local:")) {
            String suffix = filePath.substring("local:".length());
            int slash = suffix.indexOf('/');
            if (slash > 0) {
                long id = Long.parseLong(suffix.substring(0, slash));
                String name = suffix.substring(slash + 1);
                Path path = getVideoDir(id).resolve(name);
                if (!Files.isRegularFile(path)) {
                    throw new BusinessException("视频文件不存在");
                }
                return path;
            }
        }
        Path path = Path.of(filePath);
        if (!Files.isRegularFile(path)) {
            throw new BusinessException("视频文件不存在");
        }
        return path;
    }

    public String computeSha256(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(path);
                 DigestInputStream din = new DigestInputStream(in, digest)) {
                byte[] buffer = new byte[8192];
                while (din.read(buffer) != -1) {
                    // drain
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception e) {
            throw new BusinessException("计算文件哈希失败: " + e.getMessage());
        }
    }

    public Path tempCookiesDir(Long userId) {
        Path dir = storageRoot().resolve("temp-cookies").resolve(String.valueOf(userId));
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new BusinessException("创建 Cookie 临时目录失败: " + e.getMessage());
        }
        return dir;
    }

    private void deleteRecursively(Path path) {
        if (!Files.exists(path)) {
            return;
        }
        try (var walk = Files.walk(path)) {
            walk.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best effort
                }
            });
        } catch (IOException ignored) {
            // best effort
        }
    }
}
