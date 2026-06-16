package com.dovidioai.domain.repository;

import com.dovidioai.domain.entity.Summary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SummaryRepository extends JpaRepository<Summary, Long> {

    Optional<Summary> findByVideoId(Long videoId);
}
