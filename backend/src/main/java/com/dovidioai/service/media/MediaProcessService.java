package com.dovidioai.service.media;

import com.dovidioai.config.AppProperties;
import com.dovidioai.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class MediaProcessService {

    private final AppProperties appProperties;

    public double probeDurationSeconds(Path mediaPath) {
        List<String> command = List.of(
                appProperties.getFfprobePath(),
                "-v", "error",
                "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1",
                mediaPath.toAbsolutePath().toString()
        );
        String output = runProcessWithOutput(command, 120, "ffprobe 读取时长失败");
        if (output.isBlank()) {
            throw new BusinessException("无法读取视频时长");
        }
        return Double.parseDouble(output.trim());
    }

    public void validateDuration(Path mediaPath) {
        validateHasAudioStream(mediaPath);
        double duration = probeDurationSeconds(mediaPath);
        if (duration > appProperties.getMaxDurationSeconds()) {
            throw new BusinessException("视频时长超过 " + appProperties.getMaxDurationSeconds() / 60 + " 分钟限制");
        }
    }

    public void validateHasAudioStream(Path mediaPath) {
        List<String> command = List.of(
                appProperties.getFfprobePath(),
                "-v", "error",
                "-select_streams", "a:0",
                "-show_entries", "stream=codec_type",
                "-of", "default=noprint_wrappers=1:nokey=1",
                mediaPath.toAbsolutePath().toString()
        );
        String output = runProcessWithOutput(command, 60, "ffprobe 检测音轨失败");
        if (!"audio".equalsIgnoreCase(output.trim())) {
            throw new BusinessException("视频中未检测到音频轨道，无法转写");
        }
    }

    public Path extractAudio(Path videoPath, Path outputPath) {
        try {
            Files.createDirectories(outputPath.getParent());
        } catch (IOException e) {
            throw new BusinessException("创建音频目录失败: " + e.getMessage());
        }

        List<String> command = List.of(
                appProperties.getFfmpegPath(),
                "-y",
                "-i", videoPath.toAbsolutePath().toString(),
                "-vn",
                "-acodec", "pcm_s16le",
                "-ar", "16000",
                "-ac", "1",
                outputPath.toAbsolutePath().toString()
        );
        runProcessWithOutput(command, Math.max(300, appProperties.getMaxDurationSeconds()), "ffmpeg 提取音频失败");
        try {
            if (!Files.exists(outputPath) || Files.size(outputPath) < 1024) {
                throw new BusinessException("提取的音频文件过小，可能无有效声音");
            }
        } catch (IOException e) {
            throw new BusinessException("读取音频文件失败: " + e.getMessage());
        }
        return outputPath;
    }

    public Path extractAudioFromMedia(Path mediaPath, Path outputPath) {
        String name = mediaPath.getFileName().toString().toLowerCase();
        if (name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".m4a") || name.endsWith(".aac")) {
            validateHasAudioStream(mediaPath);
            return mediaPath;
        }
        return extractAudio(mediaPath, outputPath);
    }

    private String runProcessWithOutput(List<String> command, long timeoutSeconds, String errorPrefix) {
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            Process process = builder.start();
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
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new BusinessException(errorPrefix + ": 执行超时（" + timeoutSeconds + " 秒）");
            }
            if (process.exitValue() != 0) {
                throw new BusinessException(errorPrefix + ": " + abbreviate(output));
            }
            return output;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(errorPrefix + ": " + e.getMessage());
        }
    }

    private String abbreviate(String text) {
        if (text == null || text.isBlank()) {
            return "未知错误";
        }
        return text.length() > 500 ? text.substring(0, 500) + "..." : text;
    }
}
