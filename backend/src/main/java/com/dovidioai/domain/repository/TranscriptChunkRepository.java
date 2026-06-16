package com.dovidioai.domain.repository;

import com.dovidioai.domain.entity.TranscriptChunk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TranscriptChunkRepository extends JpaRepository<TranscriptChunk, Long> {

    List<TranscriptChunk> findByVideoIdOrderByChunkIndexAsc(Long videoId);

    void deleteByVideoId(Long videoId);
}
