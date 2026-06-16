CREATE TABLE video (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT       NOT NULL DEFAULT 1,
    title           VARCHAR(512) NOT NULL,
    source_type     VARCHAR(32)  NOT NULL,
    source_url      VARCHAR(2048),
    platform        VARCHAR(64),
    platform_video_id VARCHAR(128),
    content_hash      VARCHAR(128),
    dedup_key         VARCHAR(256),
    file_path         VARCHAR(1024),
    audio_path        VARCHAR(1024),
    duration_sec      INT,
    file_size         BIGINT,
    status            VARCHAR(32)  NOT NULL,
    error_message     TEXT,
    created_at        DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    INDEX idx_video_user_id (user_id),
    INDEX idx_video_status (status),
    INDEX idx_video_dedup (user_id, dedup_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE transcript (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    video_id     BIGINT       NOT NULL,
    full_text    LONGTEXT     NOT NULL,
    segments_json LONGTEXT,
    created_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_transcript_video (video_id),
    CONSTRAINT fk_transcript_video FOREIGN KEY (video_id) REFERENCES video(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE summary (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    video_id   BIGINT       NOT NULL,
    content    LONGTEXT     NOT NULL,
    model      VARCHAR(64)  NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_summary_video (video_id),
    CONSTRAINT fk_summary_video FOREIGN KEY (video_id) REFERENCES video(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE chat_session (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    video_id   BIGINT       NOT NULL,
    user_id    BIGINT       NOT NULL DEFAULT 1,
    title      VARCHAR(256),
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_chat_session_video (video_id),
    CONSTRAINT fk_chat_session_video FOREIGN KEY (video_id) REFERENCES video(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE chat_message (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT       NOT NULL,
    role       VARCHAR(32)  NOT NULL,
    content    LONGTEXT     NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_chat_message_session (session_id),
    CONSTRAINT fk_chat_message_session FOREIGN KEY (session_id) REFERENCES chat_session(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
