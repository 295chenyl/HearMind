CREATE TABLE transcript_chunk (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    video_id     BIGINT       NOT NULL,
    chunk_index  INT          NOT NULL,
    start_sec    INT          NOT NULL,
    end_sec      INT          NOT NULL,
    chunk_text   TEXT         NOT NULL,
    embedding_json LONGTEXT,
    created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_transcript_chunk_video (video_id),
    CONSTRAINT fk_transcript_chunk_video FOREIGN KEY (video_id) REFERENCES video(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
