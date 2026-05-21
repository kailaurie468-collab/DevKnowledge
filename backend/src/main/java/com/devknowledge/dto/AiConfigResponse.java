package com.devknowledge.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

/**
 * 返回给前端的 AI 配置（API Key 脱敏）
 */
@Data
public class AiConfigResponse {

    /** 配置 ID */
    private UUID id;

    /** 配置名称 */
    private String name;

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

    /** 是否为当前激活配置 */
    private Boolean isActive;

    /**
     * 连通性测试结果
     */
    @Data
    @AllArgsConstructor
    public static class TestResult {
        private boolean success;
        private String message;
    }

    /**
     * Token 消耗统计（单日）
     */
    @Data
    @AllArgsConstructor
    public static class TokenUsage {
        private String date;
        private long tokens;
    }
}
