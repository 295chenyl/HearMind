package com.dovidioai.service.importing;

import com.dovidioai.config.AppProperties;
import com.dovidioai.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class YtDlpService {

    private static final Pattern HTTP_URL = Pattern.compile("^https?://", Pattern.CASE_INSENSITIVE);
    private static final Pattern BILIBILI_BV = Pattern.compile("(BV[a-zA-Z0-9]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern YOUTUBE_ID = Pattern.compile("(?:v=|youtu\\.be/|/shorts/)([a-zA-Z0-9_-]{6,})");

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    public record DownloadResult(String title, String platform, String platformVideoId, Path filePath) {
    }

    public record MetadataResult(String title, Integer durationSec, String platform, String platformVideoId) {
    }

    public boolean isDirectHttpUrl(String url) {
        return HTTP_URL.matcher(url.trim()).find()
                && !url.contains("bilibili.com")
                && !url.contains("youtube.com")
                && !url.contains("youtu.be");
    }

    public String detectPlatform(String url) {
        String lower = url.toLowerCase();
        if (lower.contains("bilibili.com")) {
            return "bilibili";
        }
        if (lower.contains("youtube.com") || lower.contains("youtu.be")) {
            return "youtube";
        }
        return "unknown";
    }

    public String extractPlatformVideoId(String url) {
        Matcher bv = BILIBILI_BV.matcher(url);
        if (bv.find()) {
            return bv.group(1);
        }
        Matcher yt = YOUTUBE_ID.matcher(url);
        if (yt.find()) {
            return yt.group(1);
        }
        return null;
    }

    public DownloadResult download(String url, Path outputDir, String fallbackTitle, String fallbackPlatform,
                                   String fallbackId) {
        return download(url, outputDir, fallbackTitle, fallbackPlatform, fallbackId, null);
    }

    public DownloadResult download(String url, Path outputDir, String fallbackTitle, String fallbackPlatform,
                                   String fallbackId, Path cookieFile) {
        try {
            Files.createDirectories(outputDir);
        } catch (IOException e) {
            throw new BusinessException("创建下载目录失败: " + e.getMessage());
        }

        Path template = outputDir.resolve("source.%(ext)s");
        List<String> command = buildCommand(url, List.of(
                "--no-playlist",
                "-f", "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best",
                "--merge-output-format", "mp4",
                "-o", template.toAbsolutePath().toString(),
                url
        ), cookieFile);

        String output = runCommand(command, 30, TimeUnit.MINUTES, "yt-dlp 下载失败");
        Path downloaded;
        try {
            downloaded = findDownloadedFile(outputDir);
        } catch (IOException e) {
            throw new BusinessException("查找下载文件失败: " + e.getMessage());
        }

        DownloadResult meta = tryParseJsonMetadata(output);
        String title = meta != null && meta.title() != null ? meta.title() : fallbackTitle;
        String platform = meta != null && meta.platform() != null ? meta.platform() : fallbackPlatform;
        String id = meta != null && meta.platformVideoId() != null ? meta.platformVideoId() : fallbackId;
        if (title == null || title.isBlank()) {
            title = downloaded.getFileName().toString();
        }
        if (platform == null || platform.isBlank()) {
            platform = detectPlatform(url);
        }
        if (id == null || id.isBlank()) {
            id = extractPlatformVideoId(url);
        }
        return new DownloadResult(title, platform, id, downloaded);
    }

    public MetadataResult probeMetadata(String url) {
        return probeMetadata(url, null);
    }

    public MetadataResult probeMetadata(String url, Path cookieFile) {
        List<String> command = buildCommand(url, List.of("--dump-single-json", "--no-playlist", url), cookieFile);
        String json = runCommand(command, 2, TimeUnit.MINUTES, "获取视频信息失败");
        return parseJsonMetadata(json);
    }

    public boolean isGlobalCookieConfigured() {
        return resolveCookiesFile(null) != null;
    }

    private List<String> buildCommand(String url, List<String> args, Path cookieOverride) {
        List<String> command = new ArrayList<>();
        command.add(appProperties.getYtdlpPath());
        command.add("--no-warnings");
        command.add("--ignore-config");

        addCookieArgs(command, cookieOverride);

        if (url != null && url.toLowerCase().contains("bilibili.com")) {
            command.add("--referer");
            command.add("https://www.bilibili.com/");
        }

        command.addAll(args);
        return command;
    }

    private void addCookieArgs(List<String> command, Path cookieOverride) {
        Path cookiesFile = resolveCookiesFile(cookieOverride);
        if (cookiesFile != null) {
            command.add("--cookies");
            command.add(cookiesFile.toAbsolutePath().toString());
            return;
        }
        String browser = appProperties.getYtdlpCookiesBrowser();
        if (browser != null && !browser.isBlank()) {
            command.add("--cookies-from-browser");
            command.add(browser.trim());
        }
    }

    private Path resolveCookiesFile(Path cookieOverride) {
        if (cookieOverride != null && Files.isRegularFile(cookieOverride)) {
            return cookieOverride;
        }
        String configured = appProperties.getYtdlpCookiesFile();
        if (configured == null || configured.isBlank()) {
            return null;
        }
        Path path = Path.of(configured.trim());
        if (Files.isRegularFile(path)) {
            return path;
        }
        return null;
    }

    private String runCommand(List<String> command, long timeout, TimeUnit unit, String errorPrefix) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            String output;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), Charset.defaultCharset()))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                output = sb.toString().trim();
            }
            boolean finished = process.waitFor(timeout, unit);
            if (!finished) {
                process.destroyForcibly();
                throw new BusinessException(errorPrefix + ": 执行超时");
            }
            if (process.exitValue() != 0) {
                throw new BusinessException(formatYtDlpError(output));
            }
            return output;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(errorPrefix + ": " + e.getMessage());
        }
    }

    private String formatYtDlpError(String output) {
        if (output == null || output.isBlank()) {
            return "视频下载/解析失败，请稍后重试或改为本地上传";
        }
        if (output.contains("Could not copy Chrome cookie database")) {
            return bilibiliCookieHelpMessage();
        }
        if (output.contains("HTTP Error 412") || output.contains("Precondition Failed")) {
            return "B站视频解析失败（412，需登录 Cookie）。" + bilibiliCookieHelpMessage();
        }
        if (output.contains("BiliBili")) {
            return "B站视频解析失败: " + abbreviate(singleLine(output));
        }
        return abbreviate(singleLine(output));
    }

    private String bilibiliCookieHelpMessage() {
        return "请在导入时上传或粘贴 B 站 Cookie（Netscape 格式），详见 docs/COOKIE.md；"
                + "也可由管理员配置 config/bilibili.cookies.txt 作为 fallback";
    }

    private DownloadResult tryParseJsonMetadata(String output) {
        if (output == null || output.isBlank() || !output.trim().startsWith("{")) {
            return null;
        }
        try {
            MetadataResult meta = parseJsonMetadata(output);
            return new DownloadResult(meta.title(), meta.platform(), meta.platformVideoId(), null);
        } catch (BusinessException ignored) {
            return null;
        }
    }

    private MetadataResult parseJsonMetadata(String json) {
        if (json == null || json.isBlank()) {
            throw new BusinessException("无法解析视频信息");
        }
        if (!json.trim().startsWith("{")) {
            throw new BusinessException(formatYtDlpError(json));
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            String title = node.path("title").asText("未命名视频");
            String extractor = node.path("extractor_key").asText(node.path("extractor").asText("unknown"));
            String id = node.path("id").asText("");
            int durationSec = node.path("duration").asInt(0);
            return new MetadataResult(title, durationSec > 0 ? durationSec : null,
                    normalizePlatform(extractor), id);
        } catch (IOException e) {
            throw new BusinessException("解析视频 JSON 失败: " + e.getMessage());
        }
    }

    private Path findDownloadedFile(Path dir) throws IOException {
        try (var stream = Files.list(dir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase();
                        return n.startsWith("source.") && !n.endsWith(".part");
                    })
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("未找到下载的视频文件"));
        }
    }

    private String normalizePlatform(String extractor) {
        if (extractor == null) {
            return "unknown";
        }
        String lower = extractor.toLowerCase();
        if (lower.contains("bilibili")) {
            return "bilibili";
        }
        if (lower.contains("youtube")) {
            return "youtube";
        }
        return lower;
    }

    private String singleLine(String text) {
        return text.replace('\n', ' ').replace('\r', ' ').trim();
    }

    private String abbreviate(String text) {
        return text.length() > 400 ? text.substring(0, 400) + "..." : text;
    }
}
