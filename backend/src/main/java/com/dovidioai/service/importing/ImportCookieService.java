package com.dovidioai.service.importing;

import com.dovidioai.exception.BusinessException;
import com.dovidioai.service.storage.VideoStorageFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImportCookieService {

    private final VideoStorageFacade storageFacade;

    public Path saveTempCookie(Long userId, MultipartFile cookieFile, String cookieText) {
        if ((cookieFile == null || cookieFile.isEmpty()) && (cookieText == null || cookieText.isBlank())) {
            return null;
        }
        String content;
        if (cookieFile != null && !cookieFile.isEmpty()) {
            try {
                content = new String(cookieFile.getBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new BusinessException("读取 Cookie 文件失败: " + e.getMessage());
            }
        } else {
            content = cookieText.trim();
        }
        validateNetscapeFormat(content);
        Path target = storageFacade.tempCookiesDir(userId).resolve(UUID.randomUUID() + ".txt");
        try {
            Files.writeString(target, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BusinessException("保存 Cookie 失败: " + e.getMessage());
        }
        return target;
    }

    public void deleteIfExists(String cookiePath) {
        if (cookiePath == null || cookiePath.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(Path.of(cookiePath));
        } catch (IOException e) {
            log.warn("删除临时 Cookie 失败: {}", cookiePath);
        }
    }

    public void validateNetscapeFormat(String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException("Cookie 内容为空");
        }
        boolean hasNetscapeHeader = content.contains("Netscape HTTP Cookie File")
                || content.contains("# HTTP Cookie File");
        boolean hasTabLine = content.lines().anyMatch(line -> {
            String trimmed = line.trim();
            return !trimmed.isEmpty()
                    && !trimmed.startsWith("#")
                    && trimmed.split("\t").length >= 6;
        });
        if (!hasNetscapeHeader && !hasTabLine) {
            throw new BusinessException("Cookie 格式无效，请使用 Netscape cookies.txt 格式（参考 docs/COOKIE.md）");
        }
    }
}
