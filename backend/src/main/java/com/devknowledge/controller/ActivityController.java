package com.devknowledge.controller;

import com.devknowledge.dto.ActivityRequest;
import com.devknowledge.model.UserActivity;
import com.devknowledge.security.JwtTokenProvider;
import com.devknowledge.service.ActivityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * 用户行为记录 REST API
 * 为推荐引擎提供数据采集入口
 */
@RestController
@RequestMapping("/api/activities")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 记录用户行为
     * 内置 5 分钟去重，异步写入不阻塞主流程
     */
    @PostMapping
    public Mono<ResponseEntity<Void>> recordActivity(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody ActivityRequest req) {
        UUID userId = extractUserId(authHeader);
        if (userId == null) {
            return Mono.just(ResponseEntity.status(401).<Void>build());
        }
        return activityService.recordActivity(userId, req)
                .then(Mono.just(ResponseEntity.ok().<Void>build()));
    }

    /**
     * 分页查询用户行为记录（按时间倒序）
     */
    @GetMapping
    public Mono<ResponseEntity<List<UserActivity>>> getActivities(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId = extractUserId(authHeader);
        if (userId == null) {
            return Mono.just(ResponseEntity.status(401).build());
        }
        return activityService.getUserActivities(userId, page, size)
                .map(ResponseEntity::ok);
    }

    /**
     * 清理过期行为记录
     *
     * @param keepDays 保留天数（默认 90）
     * @return 删除的记录数
     */
    @DeleteMapping("/cleanup")
    public Mono<ResponseEntity<Integer>> cleanup(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(defaultValue = "90") int keepDays) {
        UUID userId = extractUserId(authHeader);
        if (userId == null) {
            return Mono.just(ResponseEntity.status(401).build());
        }
        return activityService.cleanup(userId, keepDays)
                .map(ResponseEntity::ok);
    }

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
