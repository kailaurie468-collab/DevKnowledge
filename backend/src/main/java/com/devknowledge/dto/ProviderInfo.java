package com.devknowledge.dto;

import lombok.Data;

import java.util.List;

/**
 * AI 服务商信息（静态配置，供前端下拉选择）
 */
@Data
public class ProviderInfo {

    /** 服务商名称，如 openai / anthropic / deepseek */
    private String name;

    /** 默认 API 基础地址 */
    private String defaultBaseUrl;

    /** 支持的模型列表 */
    private List<String> models;

    public ProviderInfo(String name, String defaultBaseUrl, List<String> models) {
        this.name = name;
        this.defaultBaseUrl = defaultBaseUrl;
        this.models = models;
    }
}
