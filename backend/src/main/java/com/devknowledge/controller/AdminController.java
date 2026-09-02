package com.devknowledge.controller;

import com.devknowledge.dto.AdminErrorResponse;
import com.devknowledge.dto.AdminFeedbackResponse;
import com.devknowledge.dto.AdminOverviewResponse;
import com.devknowledge.dto.AdminPageResponse;
import com.devknowledge.dto.AdminRequestTraceResponse;
import com.devknowledge.security.AdminAccessService;
import com.devknowledge.service.AdminService;
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
            @RequestParam(defaultValue = "50") int limit) {
        if (!adminAccessService.isAdmin(authorization)) {
            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
        }
        return adminService.listErrors(limit).map(ResponseEntity::ok);
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

    @GetMapping("/feedback")
    public Mono<ResponseEntity<List<AdminFeedbackResponse>>> feedback(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(defaultValue = "50") int limit) {
        if (!adminAccessService.isAdmin(authorization)) {
            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
        }
        return adminService.listFeedback(limit).map(ResponseEntity::ok);
    }
}
