package com.devknowledge.controller;

import com.devknowledge.dto.ClientErrorReportRequest;
import com.devknowledge.model.ErrorReport;
import com.devknowledge.security.JwtTokenProvider;
import com.devknowledge.service.RequestObservabilityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * 前端错误上报接口。
 * 允许未登录用户上报，服务端只保存脱敏后的错误摘要。
 */
@RestController
@RequestMapping("/api/telemetry")
@RequiredArgsConstructor
public class TelemetryController {

    private final RequestObservabilityService observabilityService;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/errors")
    public Mono<ResponseEntity<Void>> reportError(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Request-Id", required = false) String requestHeaderId,
            @Valid @RequestBody ClientErrorReportRequest request) {
        ErrorReport report = new ErrorReport();
        report.setId(UUID.randomUUID());
        report.setRequestId(request.getRequestId() != null ? request.getRequestId() : requestHeaderId);
        report.setUserId(extractUserId(authHeader));
        report.setSource("FRONTEND");
        report.setStage(request.getStage());
        report.setErrorType(request.getErrorType());
        report.setErrorSummary(request.getErrorSummary());
        report.setErrorDetail(request.getErrorDetail());
        report.setPage(request.getPage());
        report.setAppVersion(request.getAppVersion());
        report.setUserAgent(request.getUserAgent());
        report.setEnvironment(request.getEnvironment());
        report.setDurationMs(request.getDurationMs());
        report.setCreatedAt(Instant.now());

        return observabilityService.reportError(report)
                .thenReturn(ResponseEntity.accepted().build());
    }

    private UUID extractUserId(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        try {
            return jwtTokenProvider.getUserId(authHeader.substring(7));
        } catch (Exception ignored) {
            return null;
        }
    }
}
