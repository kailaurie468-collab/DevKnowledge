package com.devknowledge.dto;

import lombok.Data;
import java.util.UUID;

/**
 * Reranker 配置请求体
 */
@Data
public class RerankerConfigRequest {
    private UUID configId;
    private String name;
    private String apiKey;
    private String baseUrl;
    private String model;
}
