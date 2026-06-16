package com.dovidioai.domain.repository;

import com.dovidioai.domain.entity.Video;
import com.dovidioai.domain.enums.VideoStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VideoRepository extends JpaRepository<Video, Long> {

    List<Video> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<Video> findByUserIdAndDedupKeyAndStatus(Long userId, String dedupKey, VideoStatus status);
}
