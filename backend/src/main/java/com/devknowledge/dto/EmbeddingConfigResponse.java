package com.devknowledge.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.UUID;

@Data
public class EmbeddingConfigResponse {
    private UUID id;
    private String name;
    private String apiKeyMasked;
    private String baseUrl;
    private Boolean isActive;

    @Data
    @AllArgsConstructor
    public static class TestResult {
        private boolean success;
        private String message;
    }
}
