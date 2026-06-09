package com.devknowledge.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RerankerService - 精排服务")
class RerankerServiceTest {

    private RerankerService rerankerService;
    private HttpServer mockServer;
    private String baseUrl;

    @BeforeEach
    void setUp() throws Exception {
        rerankerService = new RerankerService();
        // 启动本地 Mock HTTP 服务器模拟 Reranker API
        mockServer = HttpServer.create(new InetSocketAddress(0), 0);
        baseUrl = "http://localhost:" + mockServer.getAddress().getPort() + "/v1";
    }

    @AfterEach
    void tearDown() {
        if (mockServer != null) mockServer.stop(0);
    }

    // ==================== rerank 测试 ====================

    @Nested
    @DisplayName("rerank - 精排调用")
    class RerankTests {

        @Test
        @DisplayName("正常精排：返回按分数降序的结果")
        void successfulRerank() throws Exception {
            String responseJson = buildRerankResponse(
                    new double[]{0.95, 0.72, 0.31});

            mockServer.createContext("/v1/rerank", exchange -> {
                byte[] response = responseJson.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response);
                }
            });
            mockServer.start();

            List<RerankerService.RerankResult> results = rerankerService.rerank(
                    "如何配置 JWT 认证",
                    List.of("JwtTokenProvider 使用 HMAC-SHA256 签名...",
                            "Spring Security 配置类放行公开接口...",
                            "数据库迁移脚本说明..."),
                    baseUrl, "test-api-key", "Qwen/Qwen3-Reranker-0.6B", 3);

            assertThat(results).hasSize(3);
            assertThat(results.get(0).index()).isEqualTo(0);
            assertThat(results.get(0).score()).isEqualTo(0.95);
            assertThat(results.get(1).index()).isEqualTo(1);
            assertThat(results.get(1).score()).isEqualTo(0.72);
            assertThat(results.get(2).index()).isEqualTo(2);
            assertThat(results.get(2).score()).isEqualTo(0.31);
        }

        @Test
        @DisplayName("空文档列表：返回空结果")
        void emptyDocuments() {
            List<RerankerService.RerankResult> results = rerankerService.rerank(
                    "query", List.of(), baseUrl, "key", "model", 5);
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("null 文档列表：返回空结果")
        void nullDocuments() {
            List<RerankerService.RerankResult> results = rerankerService.rerank(
                    "query", null, baseUrl, "key", "model", 5);
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("API 返回 top_n 子集结果")
        void partialResults() throws Exception {
            // API 只返回 top-2，即使请求了 3 个
            String responseJson = buildRerankResponse(new double[]{0.88, 0.65});

            mockServer.createContext("/v1/rerank", exchange -> {
                byte[] response = responseJson.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response);
                }
            });
            mockServer.start();

            List<RerankerService.RerankResult> results = rerankerService.rerank(
                    "query", List.of("doc1", "doc2", "doc3"),
                    baseUrl, "key", "model", 2);

            assertThat(results).hasSize(2);
            assertThat(results.get(0).score()).isEqualTo(0.88);
        }

        @Test
        @DisplayName("API 返回 401 时抛出 API Key 无效异常")
        void api401Error() throws Exception {
            mockServer.createContext("/v1/rerank", exchange -> {
                byte[] response = "{\"error\":\"unauthorized\"}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(401, response.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response);
                }
            });
            mockServer.start();

            assertThatThrownBy(() -> rerankerService.rerank(
                    "query", List.of("doc"), baseUrl, "bad-key", "model", 1))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("API Key 无效");
        }

        @Test
        @DisplayName("请求体包含正确的字段")
        void requestBodyFormat() throws Exception {
            String responseJson = buildRerankResponse(new double[]{0.9});

            mockServer.createContext("/v1/rerank", exchange -> {
                // 验证请求体格式
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                assertThat(body).contains("\"model\":\"test-model\"");
                assertThat(body).contains("\"query\":\"test query\"");
                assertThat(body).contains("\"documents\"");
                assertThat(body).contains("\"top_n\":1");

                byte[] response = responseJson.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response);
                }
            });
            mockServer.start();

            rerankerService.rerank("test query", List.of("doc"),
                    baseUrl, "key", "test-model", 1);
        }
    }

    // ==================== testConnection 测试 ====================

    @Nested
    @DisplayName("testConnection - 连通性测试")
    class TestConnectionTests {

        @Test
        @DisplayName("连通性测试成功返回 true")
        void connectionSuccess() throws Exception {
            String responseJson = buildRerankResponse(new double[]{0.5});

            mockServer.createContext("/v1/rerank", exchange -> {
                byte[] response = responseJson.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response);
                }
            });
            mockServer.start();

            boolean ok = rerankerService.testConnection(baseUrl, "key", "model");
            assertThat(ok).isTrue();
        }

        @Test
        @DisplayName("连通性测试失败返回 false")
        void connectionFailure() {
            // 不启动 mock server，模拟连接失败
            boolean ok = rerankerService.testConnection(
                    "http://localhost:1", "key", "model");
            assertThat(ok).isFalse();
        }
    }

    // ==================== 辅助方法 ====================

    /** 构造 Reranker API 响应 JSON */
    private String buildRerankResponse(double[] scores) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"results\":[");
        for (int i = 0; i < scores.length; i++) {
            if (i > 0) sb.append(",");
            sb.append("{\"index\":").append(i)
              .append(",\"relevance_score\":").append(scores[i]).append("}");
        }
        sb.append("]}");
        return sb.toString();
    }
}
