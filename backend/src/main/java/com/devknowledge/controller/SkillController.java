package com.devknowledge.controller;

import com.devknowledge.dto.ExtractSkillRequest;
import com.devknowledge.dto.SkillExportResponse;
import com.devknowledge.dto.SkillUpdateRequest;
import com.devknowledge.model.Skill;
import com.devknowledge.model.SkillSuggestion;
import com.devknowledge.security.JwtTokenProvider;
import com.devknowledge.service.SkillExtractionService;
import com.devknowledge.service.SkillService;
import com.devknowledge.service.SkillSuggestionService;
import com.devknowledge.service.ai.AiChunk;
import com.devknowledge.service.ai.AiChunkType;
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
 * Skill REST API
 * 包含 Skill CRUD、SSE 流式提取、Markdown 导出、推荐管理
 */
@RestController
@RequestMapping("/api/skills")
@RequiredArgsConstructor
public class SkillController {

    private final SkillService skillService;
    private final SkillExtractionService extractionService;
    private final SkillSuggestionService suggestionService;
    private final JwtTokenProvider jwtTokenProvider;

    // ==================== Skill 提取（SSE 流式） ====================

    /**
     * SSE 流式提取 Skill
     * 认证可选：匿名用户也能提取，但登录用户会自动保存
     */
    @PostMapping(value = "/extract", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> extract(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody ExtractSkillRequest req) {

        UUID userId = extractUserId(authHeader);
        StringBuilder accumulated = new StringBuilder();

        return extractionService.extractSkill(userId, req)
                .map(chunk -> {
                    if (chunk.getType() == AiChunkType.TEXT) {
                        accumulated.append(chunk.getContent());
                        return ServerSentEvent.<String>builder(chunk.getContent())
                                .event("text").build();
                    } else if (chunk.getType() == AiChunkType.DONE) {
                        // 登录用户：异步保存提取结果
                        if (userId != null) {
                            extractionService.parseAndSave(userId, accumulated.toString(), req)
                                    .subscribe();
                        }
                        return ServerSentEvent.<String>builder("[DONE]")
                                .event("done").build();
                    } else if (chunk.getType() == AiChunkType.ERROR) {
                        return ServerSentEvent.<String>builder(chunk.getContent())
                                .event("error").build();
                    } else {
                        return ServerSentEvent.<String>builder("")
                                .event("ping").build();
                    }
                });
    }

    // ==================== Skill CRUD ====================

    /**
     * 获取用户的 Skill 列表（分页 + 过滤）
     */
    @GetMapping
    public Mono<ResponseEntity<com.baomidou.mybatisplus.extension.plugins.pagination.Page<Skill>>> getSkills(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword) {
        UUID userId = extractUserId(authHeader);
        return skillService.getUserSkills(userId, category, keyword, page, size)
                .map(ResponseEntity::ok);
    }

    /**
     * 获取 Skill 详情（含步骤）
     */
    @GetMapping("/{id}")
    public Mono<ResponseEntity<Skill>> getSkill(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id) {
        UUID userId = extractUserId(authHeader);
        return skillService.getSkillById(id, userId)
                .map(skill -> skill != null
                        ? ResponseEntity.ok(skill)
                        : ResponseEntity.status(404).build());
    }

    /**
     * 更新 Skill（含步骤替换）
     */
    @PutMapping("/{id}")
    public Mono<ResponseEntity<Skill>> updateSkill(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id,
            @RequestBody SkillUpdateRequest req) {
        UUID userId = extractUserId(authHeader);
        return skillService.updateSkill(id, userId, req)
                .map(ResponseEntity::ok);
    }

    /**
     * 软删除 Skill
     */
    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deleteSkill(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id) {
        UUID userId = extractUserId(authHeader);
        return skillService.deleteSkill(id, userId)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }

    // ==================== 导出 ====================

    /**
     * 导出 Skill 为 Markdown
     */
    @PostMapping("/{id}/export")
    public Mono<ResponseEntity<SkillExportResponse>> exportSkill(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id) {
        UUID userId = extractUserId(authHeader);
        return skillService.exportToMarkdown(id, userId)
                .map(content -> ResponseEntity.ok(new SkillExportResponse(content)));
    }

    // ==================== 推荐管理 ====================

    /**
     * 获取用户的推荐列表（pending 状态）
     */
    @GetMapping("/suggestions")
    public Mono<ResponseEntity<List<SkillSuggestion>>> getSuggestions(
            @RequestHeader("Authorization") String authHeader) {
        UUID userId = extractUserId(authHeader);
        return suggestionService.getSuggestions(userId)
                .map(ResponseEntity::ok);
    }

    /**
     * 刷新推荐（触发规则引擎重新生成）
     */
    @PostMapping("/suggestions/refresh")
    public Mono<ResponseEntity<List<SkillSuggestion>>> refreshSuggestions(
            @RequestHeader("Authorization") String authHeader) {
        UUID userId = extractUserId(authHeader);
        return suggestionService.generateSuggestions(userId)
                .map(ResponseEntity::ok);
    }

    /**
     * 编辑推荐（采纳前修改内容）
     */
    @PutMapping("/suggestions/{id}")
    public Mono<ResponseEntity<SkillSuggestion>> updateSuggestion(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id,
            @RequestBody SkillSuggestion partial) {
        UUID userId = extractUserId(authHeader);
        return suggestionService.updateSuggestion(id, userId, partial)
                .map(ResponseEntity::ok);
    }

    /**
     * 采纳推荐 → 转为正式 Skill
     */
    @PostMapping("/suggestions/{id}/accept")
    public Mono<ResponseEntity<Skill>> acceptSuggestion(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id) {
        UUID userId = extractUserId(authHeader);
        return suggestionService.acceptSuggestion(id, userId)
                .map(ResponseEntity::ok);
    }

    /**
     * 忽略推荐
     */
    @PostMapping("/suggestions/{id}/dismiss")
    public Mono<ResponseEntity<Void>> dismissSuggestion(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable UUID id) {
        UUID userId = extractUserId(authHeader);
        return suggestionService.dismissSuggestion(id, userId)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }

    // ==================== 工具方法 ====================

    /**
     * 从 Authorization Header 提取用户 ID
     */
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
