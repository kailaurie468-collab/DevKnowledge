package com.devknowledge.dto;

import lombok.Data;
import java.time.Instant;
import java.util.UUID;

/**
 * RAG 指标响应 DTO
 */
@Data
public class RagMetricResponse {
    private UUID demoId;
    private String demoTitle;
    private UUID kbId;
    private Boolean ragUsed;
    private Integer topK;
    private Integer chunkCount;
    private Double avgSimilarity;
    private Double maxSimilarity;
    private Double minSimilarity;
    private Integer bm25Count;
    private Integer vectorCount;
    private Integer mergedCount;
    private Boolean rerankUsed;
    private Integer retrievalMs;
    private Integer toolCallCount;
    private Instant createdAt;
}
