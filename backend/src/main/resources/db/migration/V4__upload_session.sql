CREATE TABLE upload_session (
    id              VARCHAR(64)  NOT NULL PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    filename        VARCHAR(512) NOT NULL,
    file_size       BIGINT       NOT NULL,
    chunk_size      INT          NOT NULL,
    total_chunks    INT          NOT NULL,
    uploaded_chunks TEXT         NOT NULL,
    content_hash    VARCHAR(128),
    status          VARCHAR(32)  NOT NULL,
    video_id        BIGINT,
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    expires_at      DATETIME(3)  NOT NULL,
    INDEX idx_upload_session_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
