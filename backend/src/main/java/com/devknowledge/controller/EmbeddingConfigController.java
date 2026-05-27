package com.devknowledge.controller;

import com.devknowledge.dto.AiConfigResponse;
import com.devknowledge.dto.EmbeddingConfigRequest;
import com.devknowledge.dto.EmbeddingConfigResponse;
import com.devknowledge.security.JwtTokenProvider;
import com.devknowledge.service.EmbeddingConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class EmbeddingConfigController {

    private final EmbeddingConfigService embeddingConfigService;
    private final JwtTokenProvider jwtTokenProvider;

    @GetMapping("/embedding-config")
    public Mono<ResponseEntity<EmbeddingConfigResponse>> getActive(
            @RequestHeader("Authorization") String authHeader) {
        UUID userId = extractUserId(authHeader);
        return embeddingConfigService.getActiveConfigResponse(userId)
                .map(config -> config != null ? ResponseEntity.ok(config) : ResponseEntity.ok().build());
    }

    @GetMapping("/embedding-configs")
    public Mono<ResponseEntity<List<EmbeddingConfigResponse>>> getAll(
            @RequestHeader("Authorization") String authHeader) {
        UUID userId = extractUserId(authHeader);
        return embeddingConfigService.getAllConfigs(userId).map(ResponseEntity::ok);
    }

    @PutMapping("/embedding-config")
    public Mono<ResponseEntity<EmbeddingConfigResponse>> update(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody EmbeddingConfigRequest req) {
        UUID userId = extractUserId(authHeader);
        return embeddingConfigService.updateConfig(userId, req).map(ResponseEntity::ok);
    }

    @PostMapping("/embedding-configs/{id}/activate")
    public Mono<ResponseEntity<Void>> activate(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id) {
        UUID userId = extractUserId(authHeader);
        return embeddingConfigService.switchConfig(userId, id)
                .then(Mono.just(ResponseEntity.ok().<Void>build()));
    }

    @DeleteMapping("/embedding-configs/{id}")
    public Mono<ResponseEntity<Void>> delete(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id) {
        UUID userId = extractUserId(authHeader);
        return embeddingConfigService.deleteConfig(userId, id)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }

    @PostMapping("/embedding-config/test")
    public Mono<ResponseEntity<EmbeddingConfigResponse.TestResult>> test(
            @RequestHeader("Authorization") String authHeader) {
        UUID userId = extractUserId(authHeader);
        return embeddingConfigService.testConfig(userId).map(ResponseEntity::ok);
    }

    @GetMapping("/embedding-usage")
    public Mono<ResponseEntity<List<AiConfigResponse.TokenUsage>>> getUsage(
            @RequestHeader("Authorization") String authHeader) {
        UUID userId = extractUserId(authHeader);
        return embeddingConfigService.getEmbeddingUsage(userId).map(ResponseEntity::ok);
    }

    private UUID extractUserId(String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        return jwtTokenProvider.getUserId(token);
    }
}
