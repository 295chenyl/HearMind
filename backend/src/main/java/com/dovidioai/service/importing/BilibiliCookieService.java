package com.dovidioai.service.importing;

import com.dovidioai.config.AppProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class BilibiliCookieService {

    public static final String STATUS_MISSING = "MISSING";
    public static final String STATUS_OK = "OK";
    public static final String STATUS_WARN = "WARN";
    public static final String STATUS_EXPIRED = "EXPIRED";
    public static final String STATUS_INVALID = "INVALID";

    private static final String SESSDATA = "SESSDATA";
    private static final String NETSCAPE_HEADER = "# Netscape HTTP Cookie File";

    private final AppProperties appProperties;

    private Path configuredCookiePath;

    public record CookieStatus(
            boolean configured,
            String status,
            Long sessdataExpiresAtEpochSec,
            String sessdataExpiresAt,
            Integer daysUntilExpiry,
            String message
    ) {
    }

    @PostConstruct
    public void init() {
        configuredCookiePath = resolveConfiguredPath();
        if (configuredCookiePath != null) {
            log.info("B 站 Cookie 文件: {}", configuredCookiePath.toAbsolutePath());
        } else {
            log.warn("未找到 B 站 Cookie 文件，请在 config/bilibili.cookies.txt 配置（见 docs/COOKIE.md）");
        }
    }

    public Path resolveGlobalCookieFile() {
        return prepareYtDlpCookieFile();
    }

    public boolean isGlobalCookieConfigured() {
        return prepareYtDlpCookieFile() != null;
    }

    /**
     * 从配置的 cookies 文件中提取 B 站相关条目，生成供 yt-dlp 使用的精简文件。
     */
    public Path prepareYtDlpCookieFile() {
        Path source = configuredCookiePath;
        if (source == null || !Files.isRegularFile(source)) {
            return null;
        }
        try {
            String raw = readCookieText(source);
            String filtered = filterBilibiliCookies(raw);
            if (!hasBilibiliSessdata(filtered)) {
                log.warn("Cookie 文件中未找到有效的 .bilibili.com SESSDATA: {}", source.toAbsolutePath());
                return null;
            }
            Path cacheDir = Path.of(appProperties.getStoragePath(), ".cache");
            Files.createDirectories(cacheDir);
            Path cached = cacheDir.resolve("bilibili-ytdlp.cookies.txt");
            String payload = NETSCAPE_HEADER + "\n" + filtered;
            if (!Files.isRegularFile(cached) || !payload.equals(Files.readString(cached, StandardCharsets.UTF_8))) {
                Files.writeString(cached, payload, StandardCharsets.UTF_8);
                log.info("已生成 B 站精简 Cookie 缓存: {}", cached.toAbsolutePath());
            }
            return cached;
        } catch (IOException e) {
            log.warn("处理 B 站 Cookie 文件失败: {}", source, e);
            return null;
        }
    }

    public CookieStatus getStatus() {
        Path source = configuredCookiePath;
        if (source == null || !Files.isRegularFile(source)) {
            return new CookieStatus(
                    false,
                    STATUS_MISSING,
                    null,
                    null,
                    null,
                    "未配置服务器 B 站 Cookie，请在 config/bilibili.cookies.txt 写入 Cookie（见 docs/COOKIE.md）"
            );
        }

        try {
            String raw = readCookieText(source);
            String filtered = filterBilibiliCookies(raw);
            if (!hasBilibiliSessdata(filtered)) {
                return new CookieStatus(
                        false,
                        STATUS_INVALID,
                        null,
                        null,
                        null,
                        "Cookie 文件存在但未包含 .bilibili.com 的 SESSDATA，请从 B 站页面导出（勿用「全部 Cookie」中无 B 站条目的文件）"
                );
            }
            return buildStatus(filtered);
        } catch (IOException e) {
            log.warn("读取 B 站 Cookie 文件失败: {}", source, e);
            return new CookieStatus(
                    false,
                    STATUS_INVALID,
                    null,
                    null,
                    null,
                    "Cookie 文件无法读取，请检查路径与权限: " + source.toAbsolutePath()
            );
        }
    }

    private String filterBilibiliCookies(String content) {
        Set<String> seen = new LinkedHashSet<>();
        List<String> lines = new ArrayList<>();
        for (String line : content.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            String[] parts = trimmed.split("\t");
            if (parts.length < 7) {
                continue;
            }
            String domain = parts[0].trim().toLowerCase(Locale.ROOT);
            if (!isBilibiliDomain(domain)) {
                continue;
            }
            if (seen.add(trimmed)) {
                lines.add(trimmed);
            }
        }
        return String.join("\n", lines);
    }

    private String readCookieText(Path source) throws IOException {
        byte[] bytes = Files.readAllBytes(source);
        if (bytes.length >= 3 && bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF) {
            return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private boolean isBilibiliDomain(String domain) {
        return domain.equals("bilibili.com")
                || domain.endsWith(".bilibili.com")
                || domain.equals("bilibili.cn")
                || domain.endsWith(".bilibili.cn")
                || domain.equals("biligame.com")
                || domain.endsWith(".biligame.com");
    }

    private boolean hasBilibiliSessdata(String filteredContent) {
        for (String line : filteredContent.split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split("\t");
            if (parts.length < 7) {
                continue;
            }
            String domain = parts[0].trim().toLowerCase(Locale.ROOT);
            String name = parts[5].trim();
            if (SESSDATA.equalsIgnoreCase(name) && domain.endsWith("bilibili.com")) {
                return true;
            }
        }
        return false;
    }

    private Path resolveConfiguredPath() {
        String configured = appProperties.getYtdlpCookiesFile();
        if (configured == null || configured.isBlank()) {
            return null;
        }
        Path direct = Path.of(configured.trim());
        if (Files.isRegularFile(direct)) {
            return direct.toAbsolutePath().normalize();
        }
        Path fromCwd = Path.of(System.getProperty("user.dir")).resolve(configured.trim()).normalize();
        if (Files.isRegularFile(fromCwd)) {
            return fromCwd;
        }
        Path fromParent = Path.of(System.getProperty("user.dir")).getParent();
        if (fromParent != null) {
            Path sibling = fromParent.resolve("config/bilibili.cookies.txt").normalize();
            if (Files.isRegularFile(sibling)) {
                return sibling;
            }
        }
        return null;
    }

    private CookieStatus buildStatus(String filteredContent) {
        Long expiryEpoch = parseSessdataExpiry(filteredContent, ".bilibili.com");
        int warnDays = appProperties.getBilibiliCookieWarnDays();

        if (expiryEpoch == null || expiryEpoch <= 0) {
            return new CookieStatus(
                    true,
                    STATUS_OK,
                    null,
                    null,
                    null,
                    "已配置服务器 B 站 Cookie（SESSDATA 为会话 Cookie，请定期在服务器更新 config/bilibili.cookies.txt）"
            );
        }

        Instant expiresAt = Instant.ofEpochSecond(expiryEpoch);
        long daysUntil = ChronoUnit.DAYS.between(Instant.now(), expiresAt);
        String expiresAtText = LocalDateTime.ofInstant(expiresAt, ZoneId.systemDefault())
                .toString()
                .replace('T', ' ');

        if (daysUntil < 0) {
            return new CookieStatus(
                    true,
                    STATUS_EXPIRED,
                    expiryEpoch,
                    expiresAtText,
                    (int) daysUntil,
                    "B 站 Cookie 已过期（SESSDATA 于 " + expiresAtText + "），请更新 config/bilibili.cookies.txt"
            );
        }
        if (daysUntil <= warnDays) {
            return new CookieStatus(
                    true,
                    STATUS_WARN,
                    expiryEpoch,
                    expiresAtText,
                    (int) daysUntil,
                    "B 站 Cookie 将于 " + expiresAtText + " 过期（剩余约 " + daysUntil + " 天），请更新 config/bilibili.cookies.txt"
            );
        }
        return new CookieStatus(
                true,
                STATUS_OK,
                expiryEpoch,
                expiresAtText,
                (int) daysUntil,
                "服务器 B 站 Cookie 正常，SESSDATA 约 " + daysUntil + " 天后过期"
        );
    }

    private Long parseSessdataExpiry(String content, String domainSuffix) {
        Long latest = null;
        for (String line : content.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] parts = trimmed.split("\t");
            if (parts.length < 7) {
                continue;
            }
            String domain = parts[0].trim().toLowerCase(Locale.ROOT);
            String name = parts[5].trim();
            if (!SESSDATA.equalsIgnoreCase(name) || !domain.endsWith(domainSuffix)) {
                continue;
            }
            try {
                long epoch = Long.parseLong(parts[4].trim());
                if (latest == null || epoch > latest) {
                    latest = epoch;
                }
            } catch (NumberFormatException ignored) {
                // skip malformed line
            }
        }
        return latest;
    }
}
