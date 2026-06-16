package com.dovidioai.domain.repository;

import com.dovidioai.domain.entity.Transcript;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TranscriptRepository extends JpaRepository<Transcript, Long> {

    Optional<Transcript> findByVideoId(Long videoId);
}
