package com.dovidioai.domain.entity;

import com.dovidioai.domain.enums.ContentType;
import com.dovidioai.domain.enums.SourceType;
import com.dovidioai.domain.enums.VideoStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "video")
public class Video {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 512)
    private String title;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "source_type", nullable = false, length = 32)
    private SourceType sourceType;

    @Column(name = "source_url", length = 2048)
    private String sourceUrl;

    @Column(length = 64)
    private String platform;

    @Column(name = "platform_video_id", length = 128)
    private String platformVideoId;

    @Column(name = "content_hash", length = 128)
    private String contentHash;

    @Column(name = "dedup_key", length = 256)
    private String dedupKey;

    @Column(name = "file_path", length = 1024)
    private String filePath;

    @Column(name = "audio_path", length = 1024)
    private String audioPath;

    @Column(name = "duration_sec")
    private Integer durationSec;

    @Column(name = "file_size")
    private Long fileSize;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 32)
    private VideoStatus status;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "content_type", nullable = false, length = 32)
    private ContentType contentType = ContentType.GENERAL;

    @Column(name = "import_cookie_path", length = 1024)
    private String importCookiePath;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
