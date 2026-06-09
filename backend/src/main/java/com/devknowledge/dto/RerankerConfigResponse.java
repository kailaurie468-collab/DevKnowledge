package com.devknowledge.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.UUID;

/**
 * Reranker 配置响应体（API Key 脱敏）
 */
@Data
public class RerankerConfigResponse {
    private UUID id;
    private String name;
    private String apiKeyMasked;
    private String baseUrl;
    private String model;
    private Boolean isActive;

    @Data
    @AllArgsConstructor
    public static class TestResult {
        private boolean success;
        private String message;
    }
}
