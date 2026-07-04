package com.devknowledge.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

/**
 * 用户更新 AI 配置的请求体
 */
@Data
public class AiConfigRequest {

    /** 配置 ID（更新已有配置时传入） */
    private UUID configId;

    /** 配置名称（如 "DeepSeek 主力"） */
    private String name;

    /** AI 服务商（默认 openai-compatible，所有服务商走同一适配器） */
    private String provider = "openai-compatible";

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
