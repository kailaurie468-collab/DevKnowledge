package com.devknowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devknowledge.dto.AiConfigResponse;
import com.devknowledge.dto.EmbeddingConfigRequest;
import com.devknowledge.dto.EmbeddingConfigResponse;
import com.devknowledge.mapper.UserEmbeddingConfigMapper;
import com.devknowledge.model.UserEmbeddingConfig;
import com.devknowledge.security.AesUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmbeddingConfigService {

    private final UserEmbeddingConfigMapper configMapper;
    private final EmbeddingService embeddingService;
    private final EmbeddingUsageService usageService;

    @Value("${jwt.secret}")
    private String aesSecret;

    public UserEmbeddingConfig getActiveConfig(UUID userId) {
        return configMapper.selectOne(
                new LambdaQueryWrapper<UserEmbeddingConfig>()
                        .eq(UserEmbeddingConfig::getUserId, userId)
                        .eq(UserEmbeddingConfig::getIsActive, true));
    }

    public Mono<EmbeddingConfigResponse> getActiveConfigResponse(UUID userId) {
        return Mono.fromCallable(() -> {
            UserEmbeddingConfig config = getActiveConfig(userId);
            return config != null ? toResponse(config) : null;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<List<EmbeddingConfigResponse>> getAllConfigs(UUID userId) {
        return Mono.fromCallable(() -> {
            List<UserEmbeddingConfig> configs = configMapper.selectList(
                    new LambdaQueryWrapper<UserEmbeddingConfig>()
                            .eq(UserEmbeddingConfig::getUserId, userId)
                            .orderByDesc(UserEmbeddingConfig::getIsActive)
                            .orderByDesc(UserEmbeddingConfig::getUpdatedAt));
            return configs.stream().map(this::toResponse).toList();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<EmbeddingConfigResponse> updateConfig(UUID userId, EmbeddingConfigRequest req) {
        return Mono.fromCallable(() -> {
            AesUtil aes = new AesUtil(aesSecret);
            Instant now = Instant.now();
            UserEmbeddingConfig target = null;

            if (req.getConfigId() != null) {
                target = configMapper.selectById(req.getConfigId());
                if (target != null && !target.getUserId().equals(userId)) {
                    throw new RuntimeException("无权修改此配置");
                }
            }

            if (target != null) {
                target.setName(req.getName());
                target.setBaseUrl(req.getBaseUrl());
                target.setUpdatedAt(now);
                if (req.getApiKey() != null && !req.getApiKey().isBlank()) {
                    target.setApiKey(aes.encrypt(req.getApiKey()));
                }
                configMapper.updateById(target);
            } else {
                if (req.getApiKey() == null || req.getApiKey().isBlank()) {
                    throw new RuntimeException("新建配置必须提供 API Key");
                }
                deactivateAll(userId);
                target = new UserEmbeddingConfig();
                target.setId(UUID.randomUUID());
                target.setUserId(userId);
                target.setName(req.getName() != null ? req.getName() : "OpenAI Embedding");
                target.setApiKey(aes.encrypt(req.getApiKey()));
                target.setBaseUrl(req.getBaseUrl() != null ? req.getBaseUrl() : "https://api.openai.com/v1");
                target.setIsActive(true);
                target.setCreatedAt(now);
                target.setUpdatedAt(now);
                configMapper.insert(target);
            }

            activateConfig(userId, target.getId());
            return toResponse(target);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Void> switchConfig(UUID userId, UUID configId) {
        return Mono.fromRunnable(() -> {
            UserEmbeddingConfig config = configMapper.selectById(configId);
            if (config == null || !config.getUserId().equals(userId)) {
                throw new RuntimeException("配置不存在");
            }
            activateConfig(userId, configId);
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    public Mono<Void> deleteConfig(UUID userId, UUID configId) {
        return Mono.fromRunnable(() -> {
            UserEmbeddingConfig config = configMapper.selectById(configId);
            if (config == null || !config.getUserId().equals(userId)) {
                throw new RuntimeException("配置不存在");
            }
            long count = configMapper.selectCount(
                    new LambdaQueryWrapper<UserEmbeddingConfig>()
                            .eq(UserEmbeddingConfig::getUserId, userId));
            if (count <= 1) {
                throw new RuntimeException("至少保留一个 Embedding 配置");
            }
            boolean wasActive = Boolean.TRUE.equals(config.getIsActive());
            configMapper.deleteById(configId);
            if (wasActive) {
                UserEmbeddingConfig next = configMapper.selectOne(
                        new LambdaQueryWrapper<UserEmbeddingConfig>()
                                .eq(UserEmbeddingConfig::getUserId, userId)
                                .last("LIMIT 1"));
                if (next != null) activateConfig(userId, next.getId());
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    public Mono<EmbeddingConfigResponse.TestResult> testConfig(UUID userId) {
        return Mono.fromCallable(() -> {
            UserEmbeddingConfig config = getActiveConfig(userId);
            if (config == null) {
                throw new RuntimeException("请先配置 Embedding AI");
            }
            AesUtil aes = new AesUtil(aesSecret);
            String apiKey = aes.decrypt(config.getApiKey());
            boolean ok = embeddingService.testConnection(config.getBaseUrl(), apiKey);
            return ok ? new EmbeddingConfigResponse.TestResult(true, "连接成功！")
                    : new EmbeddingConfigResponse.TestResult(false, "连接失败，请检查 API Key 和 Base URL");
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<List<AiConfigResponse.TokenUsage>> getEmbeddingUsage(UUID userId) {
        return Mono.fromCallable(() -> usageService.getWeeklyUsage(userId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private void deactivateAll(UUID userId) {
        UserEmbeddingConfig deactivate = new UserEmbeddingConfig();
        deactivate.setIsActive(false);
        configMapper.update(deactivate,
                new LambdaQueryWrapper<UserEmbeddingConfig>()
                        .eq(UserEmbeddingConfig::getUserId, userId)
                        .eq(UserEmbeddingConfig::getIsActive, true));
    }

    private void activateConfig(UUID userId, UUID configId) {
        UserEmbeddingConfig activate = new UserEmbeddingConfig();
        activate.setId(configId);
        activate.setIsActive(true);
        configMapper.updateById(activate);
    }

    private EmbeddingConfigResponse toResponse(UserEmbeddingConfig config) {
        AesUtil aes = new AesUtil(aesSecret);
        EmbeddingConfigResponse resp = new EmbeddingConfigResponse();
        resp.setId(config.getId());
        resp.setName(config.getName());
        resp.setBaseUrl(config.getBaseUrl());
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
