package com.dovidioai.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class TranscriptSegmentParser {

    private final ObjectMapper objectMapper;

    public record Segment(long startMs, long endMs, String text) {
    }

    public List<Segment> parse(String segmentsJson) {
        if (segmentsJson == null || segmentsJson.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(segmentsJson);
            List<Segment> fromSentences = parseSentences(root);
            if (!fromSentences.isEmpty()) {
                return fromSentences;
            }
            List<Segment> fromUtterances = parseUtterances(root);
            if (!fromUtterances.isEmpty()) {
                return fromUtterances;
            }
        } catch (Exception ignored) {
            return List.of();
        }
        return List.of();
    }

    private List<Segment> parseSentences(JsonNode root) {
        List<Segment> segments = new ArrayList<>();
        JsonNode transcripts = root.path("transcripts");
        if (!transcripts.isArray()) {
            return segments;
        }
        for (JsonNode transcript : transcripts) {
            JsonNode sentences = transcript.path("sentences");
            if (!sentences.isArray()) {
                continue;
            }
            for (JsonNode sentence : sentences) {
                String text = sentence.path("text").asText("").trim();
                if (text.isEmpty()) {
                    continue;
                }
                long startMs = readTimeMs(sentence, "begin_time", "start_time", "start");
                long endMs = readTimeMs(sentence, "end_time", "end");
                if (endMs <= startMs) {
                    endMs = startMs + 1000;
                }
                segments.add(new Segment(startMs, endMs, text));
            }
        }
        return segments;
    }

    private List<Segment> parseUtterances(JsonNode root) {
        List<Segment> segments = new ArrayList<>();
        JsonNode utterances = root.path("utterances");
        if (!utterances.isArray()) {
            return segments;
        }
        for (JsonNode utterance : utterances) {
            String text = utterance.path("text").asText("").trim();
            if (text.isEmpty()) {
                continue;
            }
            long startMs = readTimeMs(utterance, "start_time", "begin_time", "start");
            long endMs = readTimeMs(utterance, "end_time", "end");
            if (endMs <= startMs) {
                endMs = startMs + 1000;
            }
            segments.add(new Segment(startMs, endMs, text));
        }
        return segments;
    }

    private long readTimeMs(JsonNode node, String... fields) {
        for (String field : fields) {
            if (node.has(field) && !node.path(field).isNull()) {
                JsonNode value = node.path(field);
                if (value.isNumber()) {
                    // Paraformer 非实时 ASR：begin_time/end_time 单位为毫秒
                    return Math.round(value.asDouble());
                }
            }
        }
        return 0;
    }
}
