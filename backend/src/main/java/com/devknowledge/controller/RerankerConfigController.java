package com.devknowledge.controller;

import com.devknowledge.dto.RerankerConfigRequest;
import com.devknowledge.dto.RerankerConfigResponse;
import com.devknowledge.security.JwtTokenProvider;
import com.devknowledge.service.RerankerConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Reranker 配置管理接口
 * 复用 EmbeddingConfigController 的 URL 模式
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class RerankerConfigController {

    private final RerankerConfigService rerankerConfigService;
    private final JwtTokenProvider jwtTokenProvider;

    @GetMapping("/reranker-config")
    public Mono<ResponseEntity<RerankerConfigResponse>> getActive(
            @RequestHeader("Authorization") String authHeader) {
        UUID userId = extractUserId(authHeader);
        return rerankerConfigService.getActiveConfigResponse(userId)
                .map(config -> config != null ? ResponseEntity.ok(config) : ResponseEntity.ok().build());
    }

    @GetMapping("/reranker-configs")
    public Mono<ResponseEntity<List<RerankerConfigResponse>>> getAll(
            @RequestHeader("Authorization") String authHeader) {
        UUID userId = extractUserId(authHeader);
        return rerankerConfigService.getAllConfigs(userId).map(ResponseEntity::ok);
    }

    @PutMapping("/reranker-config")
    public Mono<ResponseEntity<RerankerConfigResponse>> update(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody RerankerConfigRequest req) {
        UUID userId = extractUserId(authHeader);
        return rerankerConfigService.updateConfig(userId, req).map(ResponseEntity::ok);
    }

    @PostMapping("/reranker-configs/{id}/activate")
    public Mono<ResponseEntity<Void>> activate(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id) {
        UUID userId = extractUserId(authHeader);
        return rerankerConfigService.switchConfig(userId, id)
                .then(Mono.just(ResponseEntity.ok().<Void>build()));
    }

    @DeleteMapping("/reranker-configs/{id}")
    public Mono<ResponseEntity<Void>> delete(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id) {
        UUID userId = extractUserId(authHeader);
        return rerankerConfigService.deleteConfig(userId, id)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }

    @PostMapping("/reranker-config/test")
    public Mono<ResponseEntity<RerankerConfigResponse.TestResult>> test(
            @RequestHeader("Authorization") String authHeader) {
        UUID userId = extractUserId(authHeader);
        return rerankerConfigService.testConfig(userId).map(ResponseEntity::ok);
    }

    private UUID extractUserId(String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        return jwtTokenProvider.getUserId(token);
    }
}
