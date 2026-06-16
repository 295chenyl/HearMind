package com.dovidioai.web;

import com.dovidioai.domain.enums.ContentType;
import com.dovidioai.service.ResumableUploadService;
import com.dovidioai.service.VideoService;
import com.dovidioai.web.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api/videos")
@RequiredArgsConstructor
public class VideoController {

    private final VideoService videoService;
    private final ResumableUploadService resumableUploadService;

    @PostMapping("/upload")
    public VideoResponse upload(@RequestParam("file") MultipartFile file) {
        return videoService.upload(file);
    }

    @PostMapping("/upload/init")
    public UploadSessionResponse initUpload(@Valid @RequestBody UploadInitRequest request) {
        return resumableUploadService.init(request);
    }

    @GetMapping("/upload/sessions")
    public List<UploadSessionResponse> listUploadSessions() {
        return resumableUploadService.listResumable();
    }

    @GetMapping("/upload/{sessionId}")
    public UploadSessionResponse uploadStatus(@PathVariable String sessionId) {
        return resumableUploadService.getStatus(sessionId);
    }

    @PutMapping("/upload/{sessionId}/chunks/{chunkIndex}")
    public UploadSessionResponse uploadChunk(@PathVariable String sessionId,
                                             @PathVariable int chunkIndex,
                                             @RequestParam("file") MultipartFile file) {
        return resumableUploadService.uploadChunk(sessionId, chunkIndex, file);
    }

    @PostMapping("/upload/{sessionId}/complete")
    public VideoResponse completeUpload(@PathVariable String sessionId) {
        return resumableUploadService.complete(sessionId);
    }

    @PostMapping(value = "/import-url/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportUrlPreviewResponse previewImportUrl(@RequestParam("url") String url,
                                                     @RequestParam(value = "cookieFile", required = false) MultipartFile cookieFile,
                                                     @RequestParam(value = "cookieText", required = false) String cookieText) {
        return videoService.previewImportUrl(url, cookieFile, cookieText);
    }

    @PostMapping(value = "/import-url", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public VideoResponse importUrl(@RequestParam("url") String url,
                                   @RequestParam(value = "cookieFile", required = false) MultipartFile cookieFile,
                                   @RequestParam(value = "cookieText", required = false) String cookieText,
                                   @RequestParam(value = "contentType", required = false, defaultValue = "GENERAL") String contentType) {
        return videoService.importUrl(url, cookieFile, cookieText, parseContentType(contentType));
    }

    @GetMapping
    public List<VideoResponse> list() {
        return videoService.listVideos();
    }

    @GetMapping("/{id}")
    public VideoResponse get(@PathVariable Long id) {
        return videoService.getVideo(id);
    }

    @GetMapping("/{id}/transcript")
    public TranscriptResponse transcript(@PathVariable Long id) {
        return videoService.getTranscript(id);
    }

    @GetMapping("/{id}/summary")
    public SummaryResponse summary(@PathVariable Long id) {
        return videoService.getSummary(id);
    }

    @PutMapping("/{id}/summary")
    public SummaryResponse updateSummary(@PathVariable Long id, @Valid @RequestBody UpdateSummaryRequest request) {
        return videoService.updateSummary(id, request);
    }

    @PostMapping("/{id}/summary/regenerate")
    public SummaryResponse regenerateSummary(@PathVariable Long id) {
        return videoService.regenerateSummary(id);
    }

    @PutMapping("/{id}/content-type")
    public VideoResponse updateContentType(@PathVariable Long id,
                                           @RequestParam("contentType") String contentType) {
        return videoService.updateContentType(id, parseContentType(contentType));
    }

    @PostMapping("/{id}/index/rebuild")
    public void rebuildIndex(@PathVariable Long id) {
        videoService.rebuildIndex(id);
    }

    @GetMapping("/{id}/export/markdown")
    public ResponseEntity<byte[]> exportMarkdown(@PathVariable Long id) {
        String markdown = videoService.exportMarkdown(id);
        String filename = "video-" + id + "-notes.md";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "markdown", StandardCharsets.UTF_8))
                .body(markdown.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/{id}/stream")
    public ResponseEntity<?> stream(@PathVariable Long id) {
        String redirectUrl = videoService.getVideoStreamRedirectUrl(id);
        if (redirectUrl != null) {
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(redirectUrl)).build();
        }
        Path path = videoService.getVideoFilePath(id);
        Resource resource = new FileSystemResource(path);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + path.getFileName() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    @PostMapping("/{id}/retry")
    public VideoResponse retry(@PathVariable Long id) {
        return videoService.retry(id);
    }

    private ContentType parseContentType(String value) {
        if (value == null || value.isBlank()) {
            return ContentType.GENERAL;
        }
        try {
            return ContentType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ContentType.GENERAL;
        }
    }
}
