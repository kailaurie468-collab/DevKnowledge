package com.devknowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devknowledge.dto.RerankerConfigRequest;
import com.devknowledge.dto.RerankerConfigResponse;
import com.devknowledge.mapper.UserRerankerConfigMapper;
import com.devknowledge.model.UserRerankerConfig;
import com.devknowledge.security.AesUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Reranker 配置管理服务
 * 复用 EmbeddingConfig 的 CRUD 模式
 */
@Service
@RequiredArgsConstructor
public class RerankerConfigService {

    private final UserRerankerConfigMapper configMapper;
    private final RerankerService rerankerService;

    @Value("${jwt.secret}")
    private String aesSecret;

    /** 获取用户当前激活的 Reranker 配置（阻塞调用，供检索流程内部使用） */
    public UserRerankerConfig getActiveConfig(UUID userId) {
        return configMapper.selectOne(
                new LambdaQueryWrapper<UserRerankerConfig>()
                        .eq(UserRerankerConfig::getUserId, userId)
                        .eq(UserRerankerConfig::getIsActive, true));
    }

    public Mono<RerankerConfigResponse> getActiveConfigResponse(UUID userId) {
        return Mono.fromCallable(() -> {
            UserRerankerConfig config = getActiveConfig(userId);
            return config != null ? toResponse(config) : null;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<List<RerankerConfigResponse>> getAllConfigs(UUID userId) {
        return Mono.fromCallable(() -> {
            List<UserRerankerConfig> configs = configMapper.selectList(
                    new LambdaQueryWrapper<UserRerankerConfig>()
                            .eq(UserRerankerConfig::getUserId, userId)
                            .orderByDesc(UserRerankerConfig::getIsActive)
                            .orderByDesc(UserRerankerConfig::getUpdatedAt));
            return configs.stream().map(this::toResponse).toList();
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<RerankerConfigResponse> updateConfig(UUID userId, RerankerConfigRequest req) {
        return Mono.fromCallable(() -> {
            AesUtil aes = new AesUtil(aesSecret);
            Instant now = Instant.now();
            UserRerankerConfig target = null;

            if (req.getConfigId() != null) {
                target = configMapper.selectById(req.getConfigId());
                if (target != null && !target.getUserId().equals(userId)) {
                    throw new RuntimeException("无权修改此配置");
                }
            }

            if (target != null) {
                // 更新已有配置
                target.setName(req.getName());
                if (req.getBaseUrl() != null) target.setBaseUrl(req.getBaseUrl());
                if (req.getModel() != null) target.setModel(req.getModel());
                target.setUpdatedAt(now);
                if (req.getApiKey() != null && !req.getApiKey().isBlank()) {
                    target.setApiKey(aes.encrypt(req.getApiKey()));
                }
                configMapper.updateById(target);
            } else {
                // 新建配置
                if (req.getApiKey() == null || req.getApiKey().isBlank()) {
                    throw new RuntimeException("新建配置必须提供 API Key");
                }
                deactivateAll(userId);
                target = new UserRerankerConfig();
                target.setId(UUID.randomUUID());
                target.setUserId(userId);
                target.setName(req.getName() != null ? req.getName() : "Qwen3 Reranker");
                target.setApiKey(aes.encrypt(req.getApiKey()));
                target.setBaseUrl(req.getBaseUrl() != null ? req.getBaseUrl() : "https://api.siliconflow.cn/v1");
                target.setModel(req.getModel() != null ? req.getModel() : "Qwen/Qwen3-Reranker-0.6B");
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
            UserRerankerConfig config = configMapper.selectById(configId);
            if (config == null || !config.getUserId().equals(userId)) {
                throw new RuntimeException("配置不存在");
            }
            activateConfig(userId, configId);
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    public Mono<Void> deleteConfig(UUID userId, UUID configId) {
        return Mono.fromRunnable(() -> {
            UserRerankerConfig config = configMapper.selectById(configId);
            if (config == null || !config.getUserId().equals(userId)) {
                throw new RuntimeException("配置不存在");
            }
            long count = configMapper.selectCount(
                    new LambdaQueryWrapper<UserRerankerConfig>()
                            .eq(UserRerankerConfig::getUserId, userId));
            if (count <= 1) {
                throw new RuntimeException("至少保留一个 Reranker 配置");
            }
            boolean wasActive = Boolean.TRUE.equals(config.getIsActive());
            configMapper.deleteById(configId);
            if (wasActive) {
                UserRerankerConfig next = configMapper.selectOne(
                        new LambdaQueryWrapper<UserRerankerConfig>()
                                .eq(UserRerankerConfig::getUserId, userId)
                                .last("LIMIT 1"));
                if (next != null) activateConfig(userId, next.getId());
            }
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    /** 测试当前激活的 Reranker 配置连通性 */
    public Mono<RerankerConfigResponse.TestResult> testConfig(UUID userId) {
        return Mono.fromCallable(() -> {
            UserRerankerConfig config = getActiveConfig(userId);
            if (config == null) {
                throw new RuntimeException("请先配置 Reranker");
            }
            AesUtil aes = new AesUtil(aesSecret);
            String apiKey = aes.decrypt(config.getApiKey());
            boolean ok = rerankerService.testConnection(config.getBaseUrl(), apiKey, config.getModel());
            return ok ? new RerankerConfigResponse.TestResult(true, "连接成功！")
                    : new RerankerConfigResponse.TestResult(false, "连接失败，请检查 API Key 和 Base URL");
        }).subscribeOn(Schedulers.boundedElastic());
    }

    private void deactivateAll(UUID userId) {
        UserRerankerConfig deactivate = new UserRerankerConfig();
        deactivate.setIsActive(false);
        configMapper.update(deactivate,
                new LambdaQueryWrapper<UserRerankerConfig>()
                        .eq(UserRerankerConfig::getUserId, userId)
                        .eq(UserRerankerConfig::getIsActive, true));
    }

    private void activateConfig(UUID userId, UUID configId) {
        UserRerankerConfig activate = new UserRerankerConfig();
        activate.setId(configId);
        activate.setIsActive(true);
        configMapper.updateById(activate);
    }

    private RerankerConfigResponse toResponse(UserRerankerConfig config) {
        AesUtil aes = new AesUtil(aesSecret);
        RerankerConfigResponse resp = new RerankerConfigResponse();
        resp.setId(config.getId());
        resp.setName(config.getName());
        resp.setBaseUrl(config.getBaseUrl());
        resp.setModel(config.getModel());
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
