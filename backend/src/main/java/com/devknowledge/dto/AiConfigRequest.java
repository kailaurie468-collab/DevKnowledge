package com.devknowledge.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 用户更新 AI 配置的请求体
 */
@Data
public class AiConfigRequest {

    /** AI 服务商：openai / anthropic / deepseek / custom */
    @NotBlank
    private String provider;

    /** 用户的 API Key（明文，后端加密后存储） */
    private String apiKey;

    /** API 基础地址 */
    @NotBlank
    private String baseUrl;

    /** 模型名称 */
    @NotBlank
    private String model;

    /** 最大输出 token 数 */
    private Integer maxTokens;
}
