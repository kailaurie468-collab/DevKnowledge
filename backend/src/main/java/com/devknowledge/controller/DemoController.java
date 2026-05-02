package com.devknowledge.controller;

import com.devknowledge.dto.GenerateDemoRequest;
import com.devknowledge.model.Demo;
import com.devknowledge.security.JwtTokenProvider;
import com.devknowledge.service.DemoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Demo 生成接口
 * 支持 SSE 流式输出和历史记录管理
 */
@RestController
@RequestMapping("/api/demos")
@RequiredArgsConstructor
public class DemoController {

    private final DemoService demoService;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 流式生成 Demo
     * 前置检查登录和 AI 配置，不通过则直接抛异常（由全局异常处理器返回 JSON）
     */
    @PostMapping(value = "/generate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> generate(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody GenerateDemoRequest req) {

        UUID userId = extractUserId(authHeader);

        // 前置检查：未登录
        if (userId == null) {
            throw new RuntimeException("请先登录并配置 AI 服务商");
        }

        // 前置检查：未配置 AI（同步检查，避免进入 SSE 流后才报错）
        if (!demoService.hasAiConfigSync(userId)) {
            throw new RuntimeException("请先在设置页配置 AI 服务商");
        }

        return demoService.generateDemo(userId, req);
    }

    @GetMapping
    public Mono<ResponseEntity<List<Demo>>> getDemos(
            @RequestHeader("Authorization") String authHeader) {
        UUID userId = extractUserId(authHeader);
        return demoService.getUserDemos(userId)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<Demo>> getDemo(@PathVariable UUID id) {
        return demoService.getDemo(id)
                .map(demo -> demo != null
                        ? ResponseEntity.ok(demo)
                        : ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deleteDemo(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id) {
        UUID userId = extractUserId(authHeader);
        return demoService.deleteDemo(id, userId)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }

    private UUID extractUserId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        try {
            return jwtTokenProvider.getUserId(authHeader.replace("Bearer ", ""));
        } catch (Exception e) {
            return null;
        }
    }
}
