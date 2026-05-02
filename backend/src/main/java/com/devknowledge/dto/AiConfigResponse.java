package com.devknowledge.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 返回给前端的 AI 配置（API Key 脱敏）
 */
@Data
public class AiConfigResponse {

    /** AI 服务商类型 */
    private String provider;

    /** 脱敏后的 API Key，如 sk-1234****abcd */
    private String apiKeyMasked;

    /** API 基础地址 */
    private String baseUrl;

    /** 模型名称 */
    private String model;

    /** 最大输出 token 数 */
    private Integer maxTokens;

    /**
     * 连通性测试结果
     */
    @Data
    @AllArgsConstructor
    public static class TestResult {
        private boolean success;
        private String message;
    }
}
