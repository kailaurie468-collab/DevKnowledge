package com.devknowledge.controller;

import com.devknowledge.dto.AiConfigRequest;
import com.devknowledge.dto.AiConfigResponse;
import com.devknowledge.dto.ProviderInfo;
import com.devknowledge.security.JwtTokenProvider;
import com.devknowledge.service.AiConfigService;
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
    private final JwtTokenProvider jwtTokenProvider;

    // ==================== AI 配置 ====================

    /**
     * 获取当前用户的 AI 配置（API Key 脱敏返回）
     *
     * @param authHeader Authorization 请求头
     * @return AI 配置响应，未配置时返回 200 + null body
     */
    @GetMapping("/user/ai-config")
    public Mono<ResponseEntity<AiConfigResponse>> getAiConfig(
            @RequestHeader("Authorization") String authHeader) {
        UUID userId = extractUserId(authHeader);
        return aiConfigService.getConfig(userId)
                .map(config -> config != null
                        ? ResponseEntity.ok(config)
                        : ResponseEntity.ok().build());
    }

    /**
     * 创建或更新用户的 AI 配置
     * API Key 在后端加密存储，前端提交明文
     *
     * @param authHeader Authorization 请求头
     * @param req        AI 配置请求体
     * @return 更新后的配置（脱敏）
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
     * 测试当前 AI 配置的连通性
     * 向服务商发送轻量级请求验证配置是否正确
     *
     * @param authHeader Authorization 请求头
     * @return 测试结果（success + message）
     */
    @PostMapping("/user/ai-config/test")
    public Mono<ResponseEntity<AiConfigResponse.TestResult>> testAiConfig(
            @RequestHeader("Authorization") String authHeader) {
        UUID userId = extractUserId(authHeader);
        return aiConfigService.testConfig(userId)
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
