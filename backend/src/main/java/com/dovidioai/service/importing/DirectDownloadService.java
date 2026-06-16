package com.dovidioai.service.importing;

import com.dovidioai.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class DirectDownloadService {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    public Path download(String url, Path outputPath) {
        try {
            Files.createDirectories(outputPath.getParent());
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url.trim()))
                    .timeout(Duration.ofMinutes(30))
                    .GET()
                    .build();
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() >= 400) {
                throw new BusinessException("直链下载失败，HTTP " + response.statusCode());
            }
            try (InputStream in = response.body()) {
                Files.copy(in, outputPath, StandardCopyOption.REPLACE_EXISTING);
            }
            return outputPath;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("直链下载失败: " + e.getMessage());
        }
    }
}
