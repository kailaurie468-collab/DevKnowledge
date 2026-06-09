package com.devknowledge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

/**
 * Qwen3-Reranker 精排服务
 * 通过交叉编码器对候选文档重排序，提升检索精度
 */
@Service
public class RerankerService {

    private static final Logger log = LoggerFactory.getLogger(RerankerService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 调用 Reranker API 对候选集进行精排
     *
     * @param query     查询文本
     * @param documents 候选文档文本列表
     * @param baseUrl   API 地址（如 https://api.siliconflow.cn/v1）
     * @param apiKey    API Key
     * @param model     模型名（如 Qwen/Qwen3-Reranker-0.6B）
     * @param topN      返回前 N 个结果
     * @return 按相关性分数降序排列的精排结果列表
     */
    public List<RerankResult> rerank(String query, List<String> documents,
                                      String baseUrl, String apiKey, String model, int topN) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }

        WebClient client = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("query", query);
        body.put("documents", documents);
        body.put("top_n", topN);

        log.info("Reranker 请求: model={}, docs={}, topN={}", model, documents.size(), topN);

        try {
            String responseStr = client.post()
                    .uri("/rerank")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(responseStr);
            JsonNode results = root.get("results");

            List<RerankResult> rerankResults = new ArrayList<>();
            for (JsonNode item : results) {
                int index = item.get("index").asInt();
                double score = item.get("relevance_score").asDouble();
                rerankResults.add(new RerankResult(index, score));
            }

            log.info("Reranker 完成: {} 条结果", rerankResults.size());
            return rerankResults;

        } catch (Exception e) {
            String msg = e.getMessage();
            log.error("Reranker 调用失败: {}", msg);
            if (msg != null && msg.contains("401")) {
                throw new RuntimeException("Reranker API Key 无效");
            }
            throw new RuntimeException("Reranker 调用失败: " + msg);
        }
    }

    /**
     * 测试 Reranker API 连通性
     */
    public boolean testConnection(String baseUrl, String apiKey, String model) {
        try {
            rerank("test", List.of("test document"), baseUrl, apiKey, model, 1);
            return true;
        } catch (Exception e) {
            log.warn("Reranker 连通性测试失败: {}", e.getMessage());
            return false;
        }
    }

    /** 精排结果：原始索引 + 相关性分数 */
    public record RerankResult(int index, double score) {}
}
