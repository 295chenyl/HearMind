package com.dovidioai.domain.repository;

import com.dovidioai.domain.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    List<ChatSession> findByVideoIdOrderByCreatedAtDesc(Long videoId);

    Optional<ChatSession> findFirstByVideoIdAndUserIdOrderByCreatedAtDesc(Long videoId, Long userId);
}
