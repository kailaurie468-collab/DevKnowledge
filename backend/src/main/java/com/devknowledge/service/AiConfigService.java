package com.devknowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devknowledge.dto.AiConfigRequest;
import com.devknowledge.dto.AiConfigResponse;
import com.devknowledge.dto.ProviderInfo;
import com.devknowledge.mapper.UserAiConfigMapper;
import com.devknowledge.model.UserAiConfig;
import com.devknowledge.security.AesUtil;
import com.devknowledge.service.ai.AiProviderAdapter;
import com.devknowledge.service.ai.AiProviderFactory;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * AI 配置服务
 * 负责用户 AI 服务商配置的增删改查和连通性测试
 */
@Service
@RequiredArgsConstructor
public class AiConfigService {

    private static final Logger log = LoggerFactory.getLogger(AiConfigService.class);

    private final UserAiConfigMapper aiConfigMapper;
    private final AiProviderFactory aiProviderFactory;

    @Value("${jwt.secret}")
    private String aesSecret;

    // ==================== 支持的 AI 服务商列表（静态数据） ====================

    /**
     * 获取所有支持的 AI 服务商信息
     * 包含默认 Base URL 和支持的模型列表
     */
    public List<ProviderInfo> getProviders() {
        return List.of(
                new ProviderInfo("openai", "https://api.openai.com/v1",
                        List.of("gpt-4o", "gpt-4o-mini", "gpt-4-turbo", "gpt-3.5-turbo")),
                new ProviderInfo("anthropic", "https://api.anthropic.com",
                        List.of("claude-sonnet-4-20250514", "claude-haiku-4-5-20251001", "claude-opus-4-7")),
                new ProviderInfo("deepseek", "https://api.deepseek.com/v1",
                        List.of("deepseek-chat", "deepseek-coder")),
                new ProviderInfo("xiaomi", "https://api.xiaomi.com/v1",
                        List.of("MiLM-6B", "MiLM-13B")),
                new ProviderInfo("custom", "", List.of())
        );
    }

    // ==================== 获取用户 AI 配置 ====================

    /**
     * 获取当前用户的 AI 配置
     * API Key 脱敏返回，不暴露原始密钥
     *
     * @param userId 当前登录用户 ID
     * @return 脱敏后的 AI 配置，未配置时返回 null
     */
    public Mono<AiConfigResponse> getConfig(UUID userId) {
        return Mono.fromCallable(() -> {
            UserAiConfig config = aiConfigMapper.selectOne(
                    new LambdaQueryWrapper<UserAiConfig>().eq(UserAiConfig::getUserId, userId));
            if (config == null) {
                return null;
            }
            return toResponse(config);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    // ==================== 更新用户 AI 配置 ====================

    /**
     * 创建或更新用户的 AI 配置
     * 如果用户已有配置则更新，否则新建
     * API Key 在存储前进行 AES 加密
     *
     * @param userId 当前登录用户 ID
     * @param req    AI 配置请求体
     * @return 更新后的配置（脱敏）
     */
    public Mono<AiConfigResponse> updateConfig(UUID userId, AiConfigRequest req) {
        return Mono.fromCallable(() -> {
            AesUtil aes = new AesUtil(aesSecret);
            Instant now = Instant.now();

            UserAiConfig existing = aiConfigMapper.selectOne(
                    new LambdaQueryWrapper<UserAiConfig>().eq(UserAiConfig::getUserId, userId));

            if (existing != null) {
                // 更新已有配置
                existing.setProvider(req.getProvider());
                existing.setBaseUrl(req.getBaseUrl());
                existing.setModel(req.getModel());
                existing.setMaxTokens(req.getMaxTokens() != null ? req.getMaxTokens() : 4096);
                existing.setUpdatedAt(now);

                // API Key 不为空时才更新（避免覆盖）
                if (req.getApiKey() != null && !req.getApiKey().isBlank()) {
                    existing.setApiKey(aes.encrypt(req.getApiKey()));
                }

                aiConfigMapper.updateById(existing);
                return toResponse(existing);
            } else {
                // 新建配置
                if (req.getApiKey() == null || req.getApiKey().isBlank()) {
                    throw new RuntimeException("首次配置必须提供 API Key");
                }

                UserAiConfig config = new UserAiConfig();
                config.setId(UUID.randomUUID());
                config.setUserId(userId);
                config.setProvider(req.getProvider());
                config.setApiKey(aes.encrypt(req.getApiKey()));
                config.setBaseUrl(req.getBaseUrl());
                config.setModel(req.getModel());
                config.setMaxTokens(req.getMaxTokens() != null ? req.getMaxTokens() : 4096);
                config.setCreatedAt(now);
                config.setUpdatedAt(now);

                aiConfigMapper.insert(config);
                return toResponse(config);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    // ==================== 测试连通性 ====================

    /**
     * 测试当前用户的 AI 配置是否可用
     * 向服务商发送一个轻量级请求，验证 API Key、Base URL、模型是否正确
     *
     * @param userId 当前登录用户 ID
     * @return 测试结果（success + message）
     */
    public Mono<AiConfigResponse.TestResult> testConfig(UUID userId) {
        // 1. 加载配置
        return Mono.fromCallable(() -> {
            UserAiConfig config = aiConfigMapper.selectOne(
                    new LambdaQueryWrapper<UserAiConfig>().eq(UserAiConfig::getUserId, userId));
            if (config == null) {
                throw new RuntimeException("请先配置 AI 服务商");
            }
            AesUtil aes = new AesUtil(aesSecret);
            config.setApiKey(aes.decrypt(config.getApiKey()));
            return config;
        }).subscribeOn(Schedulers.boundedElastic())
        // 2. 发送测试请求
        .flatMap(config -> {
            AiProviderAdapter adapter;
            try {
                adapter = aiProviderFactory.getAdapter(config.getProvider());
            } catch (Exception e) {
                return Mono.just(new AiConfigResponse.TestResult(false, "不支持的服务商: " + config.getProvider()));
            }

            log.info("测试 AI 连接: provider={}, model={}, baseUrl={}", config.getProvider(), config.getModel(), config.getBaseUrl());

            // 用最小的 max_tokens 发送测试请求
            UserAiConfig testConfig = new UserAiConfig();
            testConfig.setProvider(config.getProvider());
            testConfig.setBaseUrl(config.getBaseUrl());
            testConfig.setApiKey(config.getApiKey());
            testConfig.setModel(config.getModel());
            testConfig.setMaxTokens(10); // 只需要极少 token

            return adapter.streamCompletion("You are a test assistant.", "Reply with: OK", testConfig)
                    .filter(chunk -> !chunk.isEmpty()) // 跳过空 chunk（首个 delta 通常无 content）
                    .take(1) // 收到第一个有内容的 chunk 即可
                    .timeout(Duration.ofSeconds(30))
                    .collectList()
                    .map(chunks -> {
                        if (chunks.isEmpty()) {
                            return new AiConfigResponse.TestResult(false, "连接成功但未收到响应，请检查模型配置");
                        }
                        String reply = String.join("", chunks);
                        log.info("AI 连接测试成功，模型回复: {}", reply.substring(0, Math.min(reply.length(), 50)));
                        return new AiConfigResponse.TestResult(true,
                                "连接成功！模型 " + config.getModel() + " 响应正常");
                    })
                    .onErrorResume(e -> {
                        String msg = e.getMessage();
                        log.warn("AI 连接测试失败: {}", msg);
                        // 提供更友好的错误信息
                        if (msg != null && msg.contains("401")) {
                            return Mono.just(new AiConfigResponse.TestResult(false, "API Key 无效，请检查后重新配置"));
                        }
                        if (msg != null && msg.contains("404")) {
                            return Mono.just(new AiConfigResponse.TestResult(false, "模型不存在或 API 地址错误: " + config.getModel()));
                        }
                        if (msg != null && msg.contains("429")) {
                            return Mono.just(new AiConfigResponse.TestResult(false, "请求频率过高，请稍后再试"));
                        }
                        if (msg != null && (msg.contains("timeout") || msg.contains("Timeout"))) {
                            return Mono.just(new AiConfigResponse.TestResult(false, "连接超时，请检查网络或 Base URL"));
                        }
                        return Mono.just(new AiConfigResponse.TestResult(false, "连接失败: " + msg));
                    });
        })
        .onErrorResume(e -> Mono.just(new AiConfigResponse.TestResult(false, e.getMessage())));
    }

    /**
     * 将数据库实体转换为脱敏响应
     */
    private AiConfigResponse toResponse(UserAiConfig config) {
        AesUtil aes = new AesUtil(aesSecret);
        AiConfigResponse resp = new AiConfigResponse();
        resp.setProvider(config.getProvider());
        resp.setBaseUrl(config.getBaseUrl());
        resp.setModel(config.getModel());
        resp.setMaxTokens(config.getMaxTokens());

        // API Key 解密后脱敏
        try {
            String plainKey = aes.decrypt(config.getApiKey());
            resp.setApiKeyMasked(AesUtil.mask(plainKey));
        } catch (Exception e) {
            resp.setApiKeyMasked("****");
        }

        return resp;
    }
}
