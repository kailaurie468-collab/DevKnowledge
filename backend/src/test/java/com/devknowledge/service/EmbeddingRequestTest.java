package com.devknowledge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

/**
 * 独立测试文件：发送真实 Embedding API 请求
 * <p>
 * 运行方式（在项目根目录）：
 * mvn -pl backend compile exec:java -Dexec.mainClass="com.devknowledge.service.EmbeddingRequestTest" -Dexec.classpathScope=test
 * <p>
 * 或直接右键 main 方法运行（IDE 中）
 * <p>
 * 运行前请修改下方 BASE_URL / API_KEY / MODEL 为实际值
 */
public class EmbeddingRequestTest {

    // ============ 在此填写实际配置 ============
    private static final String BASE_URL = "https://api.laozhang.ai/v1";
    private static final String API_KEY  = "sk-bS3alycPHZKXwQQ57eBf5eE4F58041A59eDaAc3cCc534bE7";
    private static final String MODEL    = "text-embedding-3-small";
    // ==========================================

    public static void main(String[] args) {
        List<String> texts = List.of(
                "Spring Boot 是一个快速开发框架",
                "React 是前端 UI 库",
                "PostgreSQL 支持 JSONB 类型"
        );

        System.out.println("========== Embedding 请求测试 ==========");
        System.out.println("BASE_URL : " + BASE_URL);
        System.out.println("MODEL    : " + MODEL);
        System.out.println("文本数量 : " + texts.size());
        texts.forEach((t) -> System.out.println("  -> " + t));
        System.out.println();

        WebClient client = WebClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader("Authorization", "Bearer " + API_KEY)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", MODEL);
        body.put("input", texts);  // 传入 texts 列表
        body.put("dimensions", 1024);

        try {
            String responseStr = client.post()
                    .uri("/embeddings")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(responseStr);

            JsonNode data = root.get("data");
            int promptTokens = root.has("usage")
                    ? root.get("usage").get("prompt_tokens").asInt()
                    : -1;

            System.out.println("========== 响应结果 ==========");
            System.out.println("promptTokens : " + promptTokens);
            System.out.println("返回向量数   : " + (data != null ? data.size() : 0));

            if (data != null) {
                for (JsonNode item : data) {
                    int index = item.get("index").asInt();
                    int dim   = item.get("embedding").size();
                    // 打印前 5 维预览
                    List<Double> preview = new ArrayList<>();
                    for (int i = 0; i < Math.min(5, dim); i++) {
                        preview.add(item.get("embedding").get(i).asDouble());
                    }
                    System.out.printf("  [%d] 维度=%d  预览=%s...%n", index, dim, preview);
                }
            }
            System.out.println("========== 测试完成 ==========");

        } catch (Exception e) {
            System.err.println("请求失败: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            e.printStackTrace();
        }
    }
}
