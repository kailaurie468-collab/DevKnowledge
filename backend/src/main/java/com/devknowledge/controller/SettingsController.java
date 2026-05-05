package com.devknowledge.controller;

import com.devknowledge.dto.AiConfigRequest;
import com.devknowledge.dto.AiConfigResponse;
import com.devknowledge.dto.ProviderInfo;
import com.devknowledge.security.JwtTokenProvider;
import com.devknowledge.service.AiConfigService;
import com.devknowledge.service.DemoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * 设置相关接口
 * 包含 AI 配置的查询、更新、测试，以及服务商列表
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SettingsController {

    private final AiConfigService aiConfigService;
    private final DemoService demoService;
    private final JwtTokenProvider jwtTokenProvider;

    // ==================== AI 配置 ====================

    /**
     * 获取当前激活的 AI 配置（API Key 脱敏返回）
     */
    @GetMapping("/user/ai-config")
    public Mono<ResponseEntity<AiConfigResponse>> getActiveAiConfig(
            @RequestHeader("Authorization") String authHeader) {
        UUID userId = extractUserId(authHeader);
        return aiConfigService.getActiveConfig(userId)
                .map(config -> config != null
                        ? ResponseEntity.ok(config)
                        : ResponseEntity.ok().build());
    }

    /**
     * 获取用户所有 AI 配置列表
     */
    @GetMapping("/user/ai-configs")
    public Mono<ResponseEntity<List<AiConfigResponse>>> getAllConfigs(
            @RequestHeader("Authorization") String authHeader) {
        UUID userId = extractUserId(authHeader);
        return aiConfigService.getAllConfigs(userId)
                .map(ResponseEntity::ok);
    }

    /**
     * 创建或更新 AI 配置
     * 保存后自动设为激活状态
     */
    @PutMapping("/user/ai-config")
    public Mono<ResponseEntity<AiConfigResponse>> updateAiConfig(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody AiConfigRequest req) {
        UUID userId = extractUserId(authHeader);
        return aiConfigService.updateConfig(userId, req)
                .map(ResponseEntity::ok);
    }

    /**
     * 切换激活配置
     */
    @PostMapping("/user/ai-configs/{id}/activate")
    public Mono<ResponseEntity<Void>> switchConfig(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id) {
        UUID userId = extractUserId(authHeader);
        return aiConfigService.switchConfig(userId, id)
                .then(Mono.just(ResponseEntity.ok().<Void>build()));
    }

    /**
     * 删除指定配置
     */
    @DeleteMapping("/user/ai-configs/{id}")
    public Mono<ResponseEntity<Void>> deleteConfig(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id) {
        UUID userId = extractUserId(authHeader);
        return aiConfigService.deleteConfig(userId, id)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }

    /**
     * 测试当前激活配置的连通性
     */
    @PostMapping("/user/ai-config/test")
    public Mono<ResponseEntity<AiConfigResponse.TestResult>> testAiConfig(
            @RequestHeader("Authorization") String authHeader) {
        UUID userId = extractUserId(authHeader);
        return aiConfigService.testConfig(userId)
                .map(ResponseEntity::ok);
    }

    // ==================== Token 消耗统计 ====================

    /**
     * 获取近 7 天 Token 消耗统计
     */
    @GetMapping("/user/token-usage")
    public Mono<ResponseEntity<List<AiConfigResponse.TokenUsage>>> getTokenUsage(
            @RequestHeader("Authorization") String authHeader) {
        UUID userId = extractUserId(authHeader);
        return Mono.fromCallable(() -> demoService.getTokenUsage(userId))
                .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                .map(ResponseEntity::ok);
    }

    // ==================== 服务商列表 ====================

    /**
     * 获取支持的 AI 服务商列表
     * 包含各服务商的默认 Base URL 和支持的模型
     * 无需登录即可访问
     *
     * @return 服务商信息列表
     */
    @GetMapping("/providers")
    public Mono<ResponseEntity<List<ProviderInfo>>> getProviders() {
        return Mono.just(ResponseEntity.ok(aiConfigService.getProviders()));
    }

    // ==================== 工具方法 ====================

    /**
     * 从 Authorization 头解析用户 ID
     */
    private UUID extractUserId(String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        return jwtTokenProvider.getUserId(token);
    }
}
