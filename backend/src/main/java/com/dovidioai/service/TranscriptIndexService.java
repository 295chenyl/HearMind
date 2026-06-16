package com.dovidioai.service;

import com.dovidioai.domain.entity.Transcript;
import com.dovidioai.domain.entity.TranscriptChunk;
import com.dovidioai.domain.repository.TranscriptChunkRepository;
import com.dovidioai.domain.repository.TranscriptRepository;
import com.dovidioai.service.dashscope.DashScopeEmbeddingService;
import com.dovidioai.support.TranscriptSegmentParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TranscriptIndexService {

    private static final int TARGET_CHUNK_SEC = 45;
    private static final int TOP_K = 5;

    private final TranscriptRepository transcriptRepository;
    private final TranscriptChunkRepository chunkRepository;
    private final TranscriptSegmentParser segmentParser;
    private final DashScopeEmbeddingService embeddingService;
    private final ObjectMapper objectMapper;

    public record RetrievedChunk(int startSec, int endSec, String text, double score) {
    }

    @Async("videoTaskExecutor")
    public void buildIndexAsync(Long videoId) {
        try {
            buildIndex(videoId);
        } catch (Exception e) {
            log.warn("构建 RAG 索引失败 videoId={}: {}", videoId, e.getMessage());
        }
    }

    @Transactional
    public void buildIndex(Long videoId) {
        Transcript transcript = transcriptRepository.findByVideoId(videoId).orElse(null);
        if (transcript == null) {
            return;
        }
        chunkRepository.deleteByVideoId(videoId);

        List<TranscriptSegmentParser.Segment> segments = segmentParser.parse(transcript.getSegmentsJson());
        List<TextChunk> chunks = buildTextChunks(segments, transcript.getFullText());
        if (chunks.isEmpty()) {
            return;
        }

        List<String> texts = chunks.stream().map(TextChunk::text).toList();
        List<List<Double>> embeddings = embeddingService.embedBatch(texts);

        for (int i = 0; i < chunks.size(); i++) {
            TextChunk chunk = chunks.get(i);
            TranscriptChunk entity = new TranscriptChunk();
            entity.setVideoId(videoId);
            entity.setChunkIndex(i);
            entity.setStartSec(chunk.startSec());
            entity.setEndSec(chunk.endSec());
            entity.setChunkText(chunk.text());
            if (i < embeddings.size() && !embeddings.get(i).isEmpty()) {
                try {
                    entity.setEmbeddingJson(objectMapper.writeValueAsString(embeddings.get(i)));
                } catch (Exception e) {
                    log.warn("序列化 embedding 失败: {}", e.getMessage());
                }
            }
            chunkRepository.save(entity);
        }
        log.info("RAG 索引构建完成 videoId={}, chunks={}", videoId, chunks.size());
    }

    public List<RetrievedChunk> search(Long videoId, String query, int topK) {
        List<TranscriptChunk> stored = chunkRepository.findByVideoIdOrderByChunkIndexAsc(videoId);
        if (stored.isEmpty() || query == null || query.isBlank()) {
            return fallbackFromTranscript(videoId, query, topK);
        }
        try {
            List<Double> queryVector = embeddingService.embed(query);
            if (queryVector.isEmpty()) {
                return fallbackKeyword(stored, query, topK);
            }

            List<RetrievedChunk> scored = new ArrayList<>();
            for (TranscriptChunk chunk : stored) {
                List<Double> vector = readEmbedding(chunk.getEmbeddingJson());
                if (vector.isEmpty()) {
                    continue;
                }
                double score = cosineSimilarity(queryVector, vector);
                scored.add(new RetrievedChunk(chunk.getStartSec(), chunk.getEndSec(), chunk.getChunkText(), score));
            }
            if (scored.isEmpty()) {
                return fallbackKeyword(stored, query, topK);
            }
            scored.sort(Comparator.comparingDouble(RetrievedChunk::score).reversed());
            return scored.stream().limit(topK > 0 ? topK : TOP_K).toList();
        } catch (Exception e) {
            log.warn("向量检索失败 videoId={}，降级为关键词匹配: {}", videoId, e.getMessage());
            return fallbackKeyword(stored, query, topK);
        }
    }

    private List<RetrievedChunk> fallbackKeyword(List<TranscriptChunk> stored, String query, int topK) {
        if (stored.isEmpty() || query == null || query.isBlank()) {
            return List.of();
        }
        String[] terms = query.toLowerCase().split("\\s+");
        List<RetrievedChunk> scored = new ArrayList<>();
        for (TranscriptChunk chunk : stored) {
            String text = chunk.getChunkText().toLowerCase();
            int hits = 0;
            for (String term : terms) {
                if (term.length() >= 2 && text.contains(term)) {
                    hits++;
                }
            }
            if (hits > 0) {
                scored.add(new RetrievedChunk(chunk.getStartSec(), chunk.getEndSec(), chunk.getChunkText(), hits));
            }
        }
        scored.sort(Comparator.comparingDouble(RetrievedChunk::score).reversed());
        if (scored.isEmpty()) {
            return stored.stream()
                    .limit(topK > 0 ? topK : TOP_K)
                    .map(c -> new RetrievedChunk(c.getStartSec(), c.getEndSec(), c.getChunkText(), 0))
                    .toList();
        }
        return scored.stream().limit(topK > 0 ? topK : TOP_K).toList();
    }

    private List<RetrievedChunk> fallbackFromTranscript(Long videoId, String query, int topK) {
        Transcript transcript = transcriptRepository.findByVideoId(videoId).orElse(null);
        if (transcript == null) {
            return List.of();
        }
        List<TranscriptSegmentParser.Segment> segments = segmentParser.parse(transcript.getSegmentsJson());
        if (segments.isEmpty()) {
            if (transcript.getFullText() != null && !transcript.getFullText().isBlank()) {
                return List.of(new RetrievedChunk(0, 60, abbreviate(transcript.getFullText(), 500), 0));
            }
            return List.of();
        }
        if (query == null || query.isBlank()) {
            return segments.stream()
                    .limit(topK > 0 ? topK : TOP_K)
                    .map(s -> new RetrievedChunk(
                            (int) (s.startMs() / 1000),
                            (int) (s.endMs() / 1000),
                            s.text(),
                            0))
                    .toList();
        }
        String q = query.trim();
        List<RetrievedChunk> matched = segments.stream()
                .filter(s -> s.text().contains(q))
                .limit(topK > 0 ? topK : TOP_K)
                .map(s -> new RetrievedChunk(
                        (int) (s.startMs() / 1000),
                        (int) (s.endMs() / 1000),
                        s.text(),
                        2))
                .toList();
        if (!matched.isEmpty()) {
            return matched;
        }
        return segments.stream()
                .limit(topK > 0 ? topK : TOP_K)
                .map(s -> new RetrievedChunk(
                        (int) (s.startMs() / 1000),
                        (int) (s.endMs() / 1000),
                        s.text(),
                        0))
                .toList();
    }

    private String abbreviate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }

    private List<Double> readEmbedding(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private double cosineSimilarity(List<Double> a, List<Double> b) {
        int len = Math.min(a.size(), b.size());
        if (len == 0) {
            return 0;
        }
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < len; i++) {
            dot += a.get(i) * b.get(i);
            normA += a.get(i) * a.get(i);
            normB += b.get(i) * b.get(i);
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private List<TextChunk> buildTextChunks(List<TranscriptSegmentParser.Segment> segments, String fullText) {
        List<TextChunk> chunks = new ArrayList<>();
        if (segments.isEmpty()) {
            if (fullText != null && !fullText.isBlank()) {
                chunks.add(new TextChunk(0, Math.max(1, fullText.length() / 20), fullText.trim()));
            }
            return chunks;
        }

        StringBuilder buffer = new StringBuilder();
        int chunkStartSec = (int) (segments.get(0).startMs() / 1000);
        int chunkEndSec = chunkStartSec;

        for (TranscriptSegmentParser.Segment segment : segments) {
            int segStart = (int) (segment.startMs() / 1000);
            int segEnd = (int) (segment.endMs() / 1000);
            if (buffer.isEmpty()) {
                chunkStartSec = segStart;
            }
            if (!buffer.isEmpty()) {
                buffer.append(' ');
            }
            buffer.append(segment.text().trim());
            chunkEndSec = Math.max(chunkEndSec, segEnd);

            if (chunkEndSec - chunkStartSec >= TARGET_CHUNK_SEC || buffer.length() >= 500) {
                chunks.add(new TextChunk(chunkStartSec, chunkEndSec, buffer.toString().trim()));
                buffer.setLength(0);
            }
        }
        if (!buffer.isEmpty()) {
            chunks.add(new TextChunk(chunkStartSec, chunkEndSec, buffer.toString().trim()));
        }
        return chunks;
    }

    private record TextChunk(int startSec, int endSec, String text) {
    }
}
