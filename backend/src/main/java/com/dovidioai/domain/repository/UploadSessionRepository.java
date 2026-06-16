package com.dovidioai.domain.repository;

import com.dovidioai.domain.entity.UploadSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UploadSessionRepository extends JpaRepository<UploadSession, String> {

    List<UploadSession> findByUserIdAndStatusOrderByUpdatedAtDesc(Long userId, String status);

    Optional<UploadSession> findByIdAndUserId(String id, Long userId);

    Optional<UploadSession> findFirstByUserIdAndStatusAndFilenameAndFileSizeAndExpiresAtAfterOrderByUpdatedAtDesc(
            Long userId, String status, String filename, Long fileSize, LocalDateTime expiresAt);
}
