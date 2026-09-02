package com.devknowledge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.*;

@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);
    /** 全局统一的 Embedding 向量维度 */
    public static final int VECTOR_DIMENSION = 1024;
    /** text-embedding-3-small 最大 token 限制 */
    private static final int MAX_INPUT_TOKENS = 8000;
    /** 粗略估算：中文约 1.5 字符/token，英文约 4 字符/token */
    private static final int MAX_CHARS_PER_TEXT = MAX_INPUT_TOKENS * 2;
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
        // 提升 maxInMemorySize 到 2MB：批量嵌入(15×1024维)响应体可达 400KB+，
        // 默认 256KB 会导致 "200 OK" WebClientResponseException
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(c -> c.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();

        WebClient client = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .exchangeStrategies(strategies)
                .build();

        // 截断超长文本，避免超出 token 限制
        List<String> truncatedTexts = texts.stream()
                .map(text -> text.length() > MAX_CHARS_PER_TEXT ? text.substring(0, MAX_CHARS_PER_TEXT) : text)
                .toList();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("input", truncatedTexts.size() == 1 ? truncatedTexts.get(0) : truncatedTexts);
        if (dimensions != null) {
            body.put("dimensions", dimensions);
        }

        // 记录文本长度，便于调试
        int maxLen = truncatedTexts.stream().mapToInt(String::length).max().orElse(0);
        int totalLen = truncatedTexts.stream().mapToInt(String::length).sum();
        log.info("Embedding 请求: model={}, texts={}, dimensions={}, maxLen={}, totalLen={}",
                model, truncatedTexts.size(), dimensions, maxLen, totalLen);

        try {
            String responseStr = client.post()
                    .uri("/embeddings")
                    .bodyValue(body)
                    .retrieve() // ← 在这里实际发出 HTTP 请求
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
                vectors.add(raw);
            }

            log.info("Embedding 完成: {} 个向量, promptTokens={}", vectors.size(), promptTokens);
            return new EmbeddingResult(vectors, promptTokens);

        } catch (WebClientResponseException e) {
            int status = e.getStatusCode().value();
            log.error("Embedding API 响应异常 [{}]: contentType={}",
                    status, e.getHeaders().getContentType());
            throw new RuntimeException("Embedding API 错误 [" + status + "]");
        } catch (Exception e) {
            log.error("Embedding 调用失败: type={}", e.getClass().getName());
            throw new RuntimeException("Embedding 调用失败", e);
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
     * 从 API 响应体中提取错误信息
     * 支持 OpenAI 格式: {"error": {"message": "..."}}
     * 支持简单格式: {"error": "..."}
     */
    private String extractApiError(String body) {
        if (body == null || body.isBlank()) return null;
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root.has("error")) {
                JsonNode error = root.get("error");
                if (error.isObject() && error.has("message")) {
                    return error.get("message").asText();
                }
                if (error.isTextual()) {
                    return error.asText();
                }
            }
            if (root.has("message")) {
                return root.get("message").asText();
            }
        } catch (Exception ignored) {
            // JSON 解析失败，返回 null
        }
        return null;
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
    public boolean testConnection(String baseUrl, String apiKey, String model) {
        try {
            embed("test", baseUrl, apiKey, model, null);
            return true;
        } catch (Exception e) {
            log.warn("Embedding 连通性测试失败: {}", e.getMessage());
            return false;
        }
    }

    /** Embedding 结果：向量列表 + token 消耗 */
    public record EmbeddingResult(List<float[]> vectors, int promptTokens) {}
}
