package com.devknowledge.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EmbeddingService - 向量化服务")
class EmbeddingServiceTest {

    private EmbeddingService embeddingService;
    private HttpServer mockServer;
    private String baseUrl;

    @BeforeEach
    void setUp() throws Exception {
        embeddingService = new EmbeddingService();
        // 启动本地 Mock HTTP 服务器模拟 Embedding API
        mockServer = HttpServer.create(new InetSocketAddress(0), 0);
        baseUrl = "http://localhost:" + mockServer.getAddress().getPort() + "/v1";
    }

    @AfterEach
    void tearDown() {
        if (mockServer != null) mockServer.stop(0);
    }

    // ==================== vectorToString 测试 ====================

    @Nested
    @DisplayName("vectorToString - 向量转 pgvector 字符串")
    class VectorToStringTests {

        @Test
        @DisplayName("标准向量转换")
        void standardVector() {
            float[] vector = {0.1f, 0.2f, 0.3f};
            String result = EmbeddingService.vectorToString(vector);
            assertThat(result).isEqualTo("[0.1,0.2,0.3]");
        }

        @Test
        @DisplayName("单元素向量")
        void singleElement() {
            float[] vector = {1.0f};
            String result = EmbeddingService.vectorToString(vector);
            assertThat(result).isEqualTo("[1.0]");
        }

        @Test
        @DisplayName("负数和零值")
        void negativeAndZero() {
            float[] vector = {-0.5f, 0.0f, 1.23f};
            String result = EmbeddingService.vectorToString(vector);
            assertThat(result).isEqualTo("[-0.5,0.0,1.23]");
        }

        @Test
        @DisplayName("空向量")
        void emptyVector() {
            float[] vector = {};
            String result = EmbeddingService.vectorToString(vector);
            assertThat(result).isEqualTo("[]");
        }
    }

    // ==================== embedBatch 测试 ====================

    @Nested
    @DisplayName("embedBatch - 批量向量化")
    class EmbedBatchTests {

        @Test
        @DisplayName("返回向量维度与 API 返回一致（无填充）")
        void returnsActualApiDimension() throws Exception {
            // Mock API 返回 1024 维向量
            int apiDimension = 1024;
            String responseJson = buildEmbeddingResponse(apiDimension);

            mockServer.createContext("/v1/embeddings", exchange -> {
                byte[] response = responseJson.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response);
                }
            });
            mockServer.start();

            // 调用 embedBatch（不传 dimensions，使用 API 默认）
            EmbeddingService.EmbeddingResult result = embeddingService.embedBatch(
                    List.of("测试文本"), baseUrl, "test-api-key",
                    "text-embedding-3-small", null);

            // 验证返回的向量维度与 API 返回一致
            assertThat(result.vectors()).hasSize(1);
            assertThat(result.vectors().get(0)).hasSize(apiDimension);
            assertThat(result.promptTokens()).isEqualTo(10);
        }

        @Test
        @DisplayName("不同维度的向量原样返回")
        void differentDimensionsReturned() throws Exception {
            // Mock API 返回 512 维向量
            int apiDimension = 512;
            String responseJson = buildEmbeddingResponse(apiDimension);

            mockServer.createContext("/v1/embeddings", exchange -> {
                byte[] response = responseJson.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response);
                }
            });
            mockServer.start();

            EmbeddingService.EmbeddingResult result = embeddingService.embedBatch(
                    List.of("hello"), baseUrl, "key", "model", null);

            assertThat(result.vectors().get(0)).hasSize(apiDimension);
        }

        @Test
        @DisplayName("传递 dimensions 参数到 API 请求体")
        void passesDimensionsParam() throws Exception {
            // Mock API 返回指定维度的向量
            int requestedDim = 256;
            String responseJson = buildEmbeddingResponse(requestedDim);

            mockServer.createContext("/v1/embeddings", exchange -> {
                // 验证请求体包含 dimensions 参数
                String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                assertThat(requestBody).contains("\"dimensions\":256");

                byte[] response = responseJson.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response);
                }
            });
            mockServer.start();

            EmbeddingService.EmbeddingResult result = embeddingService.embedBatch(
                    List.of("test"), baseUrl, "key", "model", requestedDim);

            assertThat(result.vectors().get(0)).hasSize(requestedDim);
        }

        @Test
        @DisplayName("dimensions 为 null 时不传到 API")
        void nullDimensionsNotInRequest() throws Exception {
            String responseJson = buildEmbeddingResponse(1024);

            mockServer.createContext("/v1/embeddings", exchange -> {
                String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                assertThat(requestBody).doesNotContain("dimensions");

                byte[] response = responseJson.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response);
                }
            });
            mockServer.start();

            embeddingService.embedBatch(List.of("test"), baseUrl, "key", "model", null);
        }

        @Test
        @DisplayName("批量文本返回多个向量")
        void multipleTexts() throws Exception {
            String responseJson = buildBatchEmbeddingResponse(3, 768);

            mockServer.createContext("/v1/embeddings", exchange -> {
                byte[] response = responseJson.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response);
                }
            });
            mockServer.start();

            EmbeddingService.EmbeddingResult result = embeddingService.embedBatch(
                    List.of("text1", "text2", "text3"), baseUrl, "key", "model", null);

            assertThat(result.vectors()).hasSize(3);
            for (float[] vec : result.vectors()) {
                assertThat(vec).hasSize(768);
            }
        }
    }

    // ==================== 辅助方法 ====================

    /** 构造单条 Embedding 响应 JSON */
    private String buildEmbeddingResponse(int dimension) {
        return buildBatchEmbeddingResponse(1, dimension);
    }

    /** 构造多条 Embedding 响应 JSON */
    private String buildBatchEmbeddingResponse(int count, int dimension) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"data\":[");
        for (int c = 0; c < count; c++) {
            if (c > 0) sb.append(",");
            sb.append("{\"object\":\"embedding\",\"embedding\":[");
            for (int i = 0; i < dimension; i++) {
                if (i > 0) sb.append(",");
                sb.append(String.format("%.6f", (i % 100) * 0.001));
            }
            sb.append("],\"index\":").append(c).append("}");
        }
        sb.append("],\"model\":\"text-embedding-3-small\",\"usage\":{");
        sb.append("\"prompt_tokens\":").append(count * 10);
        sb.append(",\"total_tokens\":").append(count * 10);
        sb.append("}}");
        return sb.toString();
    }
}
