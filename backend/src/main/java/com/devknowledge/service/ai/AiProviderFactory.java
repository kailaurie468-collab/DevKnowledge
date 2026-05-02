package com.devknowledge.service.ai;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * AI 适配器工厂
 * 根据 provider 名称返回对应的适配器实例
 */
@Component
public class AiProviderFactory {

    private final Map<String, AiProviderAdapter> adapterMap;

    /**
     * Spring 自动注入所有 AiProviderAdapter 实现，按 provider 名称索引
     */
    public AiProviderFactory(List<AiProviderAdapter> adapters) {
        this.adapterMap = adapters.stream()
                .collect(Collectors.toMap(AiProviderAdapter::getProviderName, Function.identity()));
    }

    /**
     * 根据 provider 名称获取适配器
     *
     * @param provider 服务商名称，如 "openai"、"deepseek"、"anthropic"
     * @return 对应的适配器
     * @throws IllegalArgumentException 不支持的 provider
     */
    public AiProviderAdapter getAdapter(String provider) {
        // anthropic 走 OpenAI 兼容格式（大多数代理服务支持）
        // 如果需要原生 Claude API，后续添加 AnthropicAdapter
        String key = mapProvider(provider);
        AiProviderAdapter adapter = adapterMap.get(key);
        if (adapter == null) {
            throw new IllegalArgumentException("不支持的 AI 服务商: " + provider);
        }
        return adapter;
    }

    /**
     * 将 provider 名称映射到适配器 key
     * 大部分服务商都兼容 OpenAI 格式
     */
    private String mapProvider(String provider) {
        return switch (provider.toLowerCase()) {
            case "openai", "deepseek", "xiaomi", "custom", "anthropic" -> "openai-compatible";
            default -> provider.toLowerCase();
        };
    }
}
