package com.dovidioai.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String storagePath = "./storage";
    private int maxDurationSeconds = 3000;
    private long maxFileSizeBytes = 2_147_483_648L;
    private String ytdlpPath = "yt-dlp";
    private String ffmpegPath = "ffmpeg";
    private String ffprobePath = "ffprobe";
    /** 可选：edge / chrome / firefox，无 cookies 文件时使用 */
    private String ytdlpCookiesBrowser = "edge";
    /** 可选：Netscape 格式 Cookie 文件，B 站导入优先使用（比读浏览器更稳定） */
    private String ytdlpCookiesFile = "../config/bilibili.cookies.txt";
    private long defaultUserId = 1L;

    @PostConstruct
    public void resolveToolPaths() {
        ffmpegPath = resolveExecutable(ffmpegPath, "ffmpeg");
        ffprobePath = resolveExecutable(ffprobePath, "ffprobe");
        ytdlpPath = resolveExecutable(ytdlpPath, "yt-dlp");
        log.info("工具路径: ffmpeg={}, ffprobe={}, yt-dlp={}", ffmpegPath, ffprobePath, ytdlpPath);
    }

    private String resolveExecutable(String configured, String fallbackName) {
        if (configured != null && Files.isRegularFile(Path.of(configured))) {
            return configured;
        }
        String fromPath = findOnPath(fallbackName);
        if (fromPath != null) {
            return fromPath;
        }
        return configured != null ? configured : fallbackName;
    }

    private String findOnPath(String name) {
        String pathEnv = System.getenv("Path");
        if (pathEnv == null) {
            return null;
        }
        String exeName = name.endsWith(".exe") ? name : name + ".exe";
        for (String dir : pathEnv.split(";")) {
            if (dir.isBlank()) {
                continue;
            }
            Path candidate = Path.of(dir.trim(), exeName);
            if (Files.isRegularFile(candidate)) {
                return candidate.toAbsolutePath().toString();
            }
        }
        try {
            Process process = new ProcessBuilder("where.exe", name).redirectErrorStream(true).start();
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return null;
            }
            if (process.exitValue() != 0) {
                return null;
            }
            try (var reader = process.inputReader()) {
                String line = reader.readLine();
                if (line != null && Files.isRegularFile(Path.of(line.trim()))) {
                    return line.trim();
                }
            }
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return null;
    }
}
