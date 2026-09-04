package com.devknowledge.controller;

import com.devknowledge.dto.AdminErrorResponse;
import com.devknowledge.dto.AdminFeedbackResponse;
import com.devknowledge.dto.AdminOverviewResponse;
import com.devknowledge.dto.AdminPageResponse;
import com.devknowledge.dto.AdminRequestTraceResponse;
import com.devknowledge.dto.AdminTraceDetailResponse;
import com.devknowledge.dto.AdminUserResponse;
import com.devknowledge.dto.FeedbackStatusRequest;
import com.devknowledge.security.AdminAccessService;
import com.devknowledge.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 开发者后台接口。
 * 不出现在普通用户导航中，仅允许配置邮箱白名单中的账号访问。
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminAccessService adminAccessService;
    private final AdminService adminService;

    @GetMapping("/overview")
    public Mono<ResponseEntity<AdminOverviewResponse>> overview(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (!adminAccessService.isAdmin(authorization)) {
            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
        }
        return adminService.getOverview().map(ResponseEntity::ok);
    }

    @GetMapping("/errors")
    public Mono<ResponseEntity<List<AdminErrorResponse>>> errors(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String requestId) {
        if (!adminAccessService.isAdmin(authorization)) {
            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
        }
        if (requestId != null && !requestId.isBlank()) {
            return adminService.listErrorsByRequestId(requestId).map(ResponseEntity::ok);
        }
        return adminService.listErrors(limit).map(ResponseEntity::ok);
    }

    /** 单条错误详情（含完整堆栈） */
    @GetMapping("/errors/{id}")
    public Mono<ResponseEntity<AdminErrorResponse>> errorDetail(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String id) {
        if (!adminAccessService.isAdmin(authorization)) {
            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
        }
        return adminService.getError(id).map(ResponseEntity::ok);
    }

    /** requestId 对应的请求链路（trace + spans） */
    @GetMapping("/traces/detail")
    public Mono<ResponseEntity<AdminTraceDetailResponse>> traceDetail(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam String requestId) {
        if (!adminAccessService.isAdmin(authorization)) {
            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
        }
        return adminService.getTraceDetail(requestId).map(ResponseEntity::ok);
    }

    /** 用户列表（分页） */
    @GetMapping("/users")
    public Mono<ResponseEntity<AdminPageResponse<AdminUserResponse>>> users(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (!adminAccessService.isAdmin(authorization)) {
            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
        }
        return adminService.listUsers(page, size).map(ResponseEntity::ok);
    }

    @GetMapping("/traces")
    public Mono<ResponseEntity<AdminPageResponse<AdminRequestTraceResponse>>> traces(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (!adminAccessService.isAdmin(authorization)) {
            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
        }
        return adminService.listTraces(page, size).map(ResponseEntity::ok);
    }

    /** 反馈分页 + 状态筛选 */
    @GetMapping("/feedback")
    public Mono<ResponseEntity<AdminPageResponse<AdminFeedbackResponse>>> feedback(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        if (!adminAccessService.isAdmin(authorization)) {
            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
        }
        return adminService.listFeedbackPage(page, size, status).map(ResponseEntity::ok);
    }

    /** 反馈状态流转 */
    @PatchMapping("/feedback/{id}/status")
    public Mono<ResponseEntity<Void>> updateFeedbackStatus(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String id,
            @Valid @RequestBody FeedbackStatusRequest request) {
        if (!adminAccessService.isAdmin(authorization)) {
            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
        }
        return adminService.updateFeedbackStatus(id, request.getStatus())
                .map(updated -> updated > 0
                        ? ResponseEntity.ok().<Void>build()
                        : ResponseEntity.status(HttpStatus.NOT_FOUND).<Void>build());
    }
}
