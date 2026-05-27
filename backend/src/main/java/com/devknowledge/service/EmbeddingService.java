package com.devknowledge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);
    private static final int VECTOR_DIMENSION = 1536;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 批量 Embedding
     *
     * @param texts      文本列表（每批最多 20 个）
     * @param baseUrl    API 地址（如 <a href="https://api.openai.com/v1">...</a> 、 <a href="https://api.laozhang.ai/v1">...</a>）
     * @param apiKey     API Key
     * @param model      模型名
     * @param dimensions 可选维度压缩（null = 模型默认）
     * @return EmbeddingResult（向量列表 + promptTokens）
     */
    public EmbeddingResult embedBatch(List<String> texts, String baseUrl, String apiKey,
                                       String model, Integer dimensions) {
        WebClient client = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("input", texts.size() == 1 ? texts.get(0) : texts);
        if (dimensions != null) {
            body.put("dimensions", dimensions);
        }

        log.info("Embedding 请求: model={}, texts={}, dimensions={}", model, texts.size(), dimensions);

        try {
            String responseStr = client.post()
                    .uri("/embeddings")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(responseStr);
            JsonNode data = root.get("data");
            int promptTokens = root.has("usage")
                    ? root.get("usage").get("prompt_tokens").asInt()
                    : 0;

            List<float[]> vectors = new ArrayList<>();
            for (JsonNode item : data) {
                JsonNode embeddingNode = item.get("embedding");
                float[] raw = new float[embeddingNode.size()];
                for (int i = 0; i < embeddingNode.size(); i++) {
                    raw[i] = (float) embeddingNode.get(i).asDouble();
                }
                vectors.add(padToTargetDimension(raw, VECTOR_DIMENSION));
            }

            log.info("Embedding 完成: {} 个向量, promptTokens={}", vectors.size(), promptTokens);
            return new EmbeddingResult(vectors, promptTokens);

        } catch (Exception e) {
            String msg = e.getMessage();
            log.error("Embedding 调用失败: {}", msg);
            if (msg != null && msg.contains("401")) {
                throw new RuntimeException("Embedding API Key 无效");
            }
            if (msg != null && msg.contains("Not supported model")) {
                throw new RuntimeException("不支持的 Embedding 模型: " + model);
            }
            throw new RuntimeException("Embedding 调用失败: " + msg);
        }
    }

    /**
     * 单条 Embedding（便捷方法）
     */
    public float[] embed(String text, String baseUrl, String apiKey,
                          String model, Integer dimensions) {
        return embedBatch(List.of(text), baseUrl, apiKey, model, dimensions).vectors().get(0);
    }

    /**
     * 将 float[] 转为 pgvector 可接受的字符串格式: "[0.1, 0.2, ...]"
     */
    public static String vectorToString(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * 测试 Embedding API 连通性
     */
    public boolean testConnection(String baseUrl, String apiKey) {
        try {
            embed("test", baseUrl, apiKey, "text-embedding-3-small", null);
            return true;
        } catch (Exception e) {
            log.warn("Embedding 连通性测试失败: {}", e.getMessage());
            return false;
        }
    }

    private float[] padToTargetDimension(float[] original, int targetDim) {
        if (original.length == targetDim) return original;
        float[] padded = new float[targetDim];
        System.arraycopy(original, 0, padded, 0, Math.min(original.length, targetDim));
        return padded;
    }

    /** Embedding 结果：向量列表 + token 消耗 */
    public record EmbeddingResult(List<float[]> vectors, int promptTokens) {}
}
