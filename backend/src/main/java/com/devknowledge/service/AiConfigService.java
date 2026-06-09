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
import com.fasterxml.jackson.databind.JsonNode;
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
     * 获取当前激活的 AI 配置（内部使用，返回原始实体）
     */
    public UserAiConfig getActiveConfigEntity(UUID userId) {
        return aiConfigMapper.selectOne(
                new LambdaQueryWrapper<UserAiConfig>()
                        .eq(UserAiConfig::getUserId, userId)
                        .eq(UserAiConfig::getIsActive, true));
    }

    /**
     * 获取当前激活的 AI 配置（脱敏返回）
     */
    public Mono<AiConfigResponse> getActiveConfig(UUID userId) {
        return Mono.fromCallable(() -> {
            UserAiConfig config = getActiveConfigEntity(userId);
            return config != null ? toResponse(config) : null;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 获取用户所有 AI 配置列表（脱敏返回）
     */
    public Mono<List<AiConfigResponse>> getAllConfigs(UUID userId) {
        return Mono.fromCallable(() -> {
            List<UserAiConfig> configs = aiConfigMapper.selectList(
                    new LambdaQueryWrapper<UserAiConfig>()
                            .eq(UserAiConfig::getUserId, userId)
                            .orderByDesc(UserAiConfig::getIsActive)
                            .orderByDesc(UserAiConfig::getUpdatedAt));
            return configs.stream().map(this::toResponse).toList();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    // ==================== 更新用户 AI 配置 ====================

    /**
     * 创建或更新用户的 AI 配置
     * 保存后自动设为激活状态，其他配置取消激活
     */
    public Mono<AiConfigResponse> updateConfig(UUID userId, AiConfigRequest req) {
        return Mono.fromCallable(() -> {
            AesUtil aes = new AesUtil(aesSecret);
            Instant now = Instant.now();

            UserAiConfig target = null;

            // 按 configId 查找已有配置
            if (req.getConfigId() != null) {
                target = aiConfigMapper.selectById(req.getConfigId());
                if (target != null && !target.getUserId().equals(userId)) {
                    throw new RuntimeException("无权修改此配置");
                }
            }

            if (target != null) {
                // 更新已有配置
                target.setName(req.getName() != null && !req.getName().isBlank()
                        ? req.getName() : req.getProvider());
                target.setProvider(req.getProvider());
                target.setBaseUrl(req.getBaseUrl());
                target.setModel(req.getModel());
                target.setMaxTokens(req.getMaxTokens() != null ? req.getMaxTokens() : 4096);
                target.setUpdatedAt(now);

                if (req.getApiKey() != null && !req.getApiKey().isBlank()) {
                    target.setApiKey(aes.encrypt(req.getApiKey()));
                }

                aiConfigMapper.updateById(target);
            } else {
                // 新建配置
                if (req.getApiKey() == null || req.getApiKey().isBlank()) {
                    throw new RuntimeException("新建配置必须提供 API Key");
                }

                // 先取消旧的激活配置，避免唯一约束冲突
                deactivateAll(userId);

                target = new UserAiConfig();
                target.setId(UUID.randomUUID());
                target.setUserId(userId);
                target.setName(req.getName() != null && !req.getName().isBlank()
                        ? req.getName() : req.getProvider());
                target.setProvider(req.getProvider());
                target.setApiKey(aes.encrypt(req.getApiKey()));
                target.setBaseUrl(req.getBaseUrl());
                target.setModel(req.getModel());
                target.setMaxTokens(req.getMaxTokens() != null ? req.getMaxTokens() : 4096);
                target.setIsActive(true);
                target.setCreatedAt(now);
                target.setUpdatedAt(now);

                aiConfigMapper.insert(target);
            }

            // 设为激活，其他配置取消激活
//            activateConfig(userId, target.getId());

            return toResponse(target);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    // ==================== 切换 / 删除 ====================

    /**
     * 切换激活配置
     */
    public Mono<Void> switchConfig(UUID userId, UUID configId) {
        return Mono.fromRunnable(() -> {
            UserAiConfig config = aiConfigMapper.selectById(configId);
            if (config == null || !config.getUserId().equals(userId)) {
                throw new RuntimeException("配置不存在");
            }
            deactivateAll(userId);
            activateConfig(userId, configId);
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    /**
     * 删除配置（至少保留一个）
     */
    public Mono<Void> deleteConfig(UUID userId, UUID configId) {
        return Mono.fromRunnable(() -> {
            UserAiConfig config = aiConfigMapper.selectById(configId);
            if (config == null || !config.getUserId().equals(userId)) {
                throw new RuntimeException("配置不存在");
            }

            long count = aiConfigMapper.selectCount(
                    new LambdaQueryWrapper<UserAiConfig>().eq(UserAiConfig::getUserId, userId));
            if (count <= 1) {
                throw new RuntimeException("至少保留一个 AI 配置");
            }

            boolean wasActive = Boolean.TRUE.equals(config.getIsActive());
            aiConfigMapper.deleteById(configId);

            // 如果删除的是激活配置，自动激活另一个
            if (wasActive) {
                UserAiConfig next = aiConfigMapper.selectOne(
                        new LambdaQueryWrapper<UserAiConfig>()
                                .eq(UserAiConfig::getUserId, userId)
                                .last("LIMIT 1"));
                if (next != null) {
                    activateConfig(userId, next.getId());
                }
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    /**
     * 取消用户所有激活配置（insert 前调用，避免唯一约束冲突）
     */
    private void deactivateAll(UUID userId) {
        UserAiConfig deactivate = new UserAiConfig();
        deactivate.setIsActive(false);
        aiConfigMapper.update(deactivate,
                new LambdaQueryWrapper<UserAiConfig>()
                        .eq(UserAiConfig::getUserId, userId)
                        .eq(UserAiConfig::getIsActive, true));
    }

    /**
     * 将指定配置设为激活，其他配置取消激活
     */
    private void activateConfig(UUID userId, UUID configId) {
        // 先取消该用户所有激活
//        UserAiConfig deactivate = new UserAiConfig();
//        deactivate.setIsActive(false);
//        aiConfigMapper.update(deactivate,
//                new LambdaQueryWrapper<UserAiConfig>()
//                        .eq(UserAiConfig::getUserId, userId)
//                        .eq(UserAiConfig::getIsActive, true));

        // 再激活目标
        UserAiConfig activate = new UserAiConfig();
        activate.setId(configId);
        activate.setIsActive(true);
        aiConfigMapper.updateById(activate);
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
        // 1. 加载激活配置
        return Mono.fromCallable(() -> {
            UserAiConfig config = getActiveConfigEntity(userId);
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
//                    .take(1) // 收到第一个有内容的 chunk 即可
//                    .timeout(Duration.ofSeconds(30))
                    .last()
//                    .collectList()
                    .map(chunks -> {
                        if (chunks.isEmpty()) {
                            return new AiConfigResponse.TestResult(false, "连接成功但未收到响应，请检查模型配置");
                        }
//                        String reply = String.join("", chunks);
                        log.info("AI 连接测试成功，模型消耗token: {}", chunks);
                        return new AiConfigResponse.TestResult(true,
                                "连接成功！模型 " + config.getModel() + " 响应正常, 消耗：" + chunks + "tokens");
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
        resp.setId(config.getId());
        resp.setName(config.getName());
        resp.setProvider(config.getProvider());
        resp.setBaseUrl(config.getBaseUrl());
        resp.setModel(config.getModel());
        resp.setMaxTokens(config.getMaxTokens());
        resp.setIsActive(Boolean.TRUE.equals(config.getIsActive()));

        try {
            String plainKey = aes.decrypt(config.getApiKey());
            resp.setApiKeyMasked(AesUtil.mask(plainKey));
        } catch (Exception e) {
            resp.setApiKeyMasked("****");
        }

        return resp;
    }
}
