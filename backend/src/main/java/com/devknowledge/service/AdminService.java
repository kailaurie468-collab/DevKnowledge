package com.devknowledge.service;

import com.devknowledge.dto.AdminErrorResponse;
import com.devknowledge.dto.AdminFeedbackResponse;
import com.devknowledge.dto.AdminOverviewResponse;
import com.devknowledge.dto.AdminPageResponse;
import com.devknowledge.dto.AdminRequestTraceResponse;
import com.devknowledge.dto.AdminTraceDetailResponse;
import com.devknowledge.dto.AdminUserResponse;
import com.devknowledge.mapper.AdminMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

/**
 * 开发者后台查询服务。
 * 所有查询均在 boundedElastic 执行，避免阻塞 WebFlux EventLoop。
 */
@Service
@RequiredArgsConstructor
public class AdminService {

    private final AdminMapper adminMapper;

    public Mono<AdminOverviewResponse> getOverview() {
        return Mono.fromCallable(() -> {
            long totalRequests = adminMapper.countRequests();
            long successfulRequests = adminMapper.countSuccessfulRequests();
            double successRate = totalRequests == 0
                    ? 0
                    : (double) successfulRequests / totalRequests * 100;

            return new AdminOverviewResponse(
                    adminMapper.countUsers(),
                    adminMapper.sumTokens(),
                    totalRequests,
                    successfulRequests,
                    successRate,
                    adminMapper.averageLatencyMs(),
                    adminMapper.p95LatencyMs(),
                    adminMapper.countErrors(),
                    adminMapper.countFeedback());
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<List<AdminErrorResponse>> listErrors(int limit) {
        return Mono.fromCallable(() -> adminMapper.listErrors(normalizeLimit(limit)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<AdminPageResponse<AdminRequestTraceResponse>> listTraces(int page, int size) {
        return Mono.fromCallable(() -> {
            int normalizedSize = normalizePageSize(size);
            int requestedPage = Math.max(page, 1);
            long total = adminMapper.countTraces();
            int totalPages = total == 0
                    ? 0
                    : (int) ((total + normalizedSize - 1) / normalizedSize);
            int actualPage = totalPages == 0 ? 1 : Math.min(requestedPage, totalPages);
            int offset = (actualPage - 1) * normalizedSize;
            List<AdminRequestTraceResponse> items = adminMapper.listTraces(offset, normalizedSize);
            return new AdminPageResponse<>(
                    items,
                    actualPage,
                    normalizedSize,
                    total,
                    totalPages,
                    totalPages > 0 && actualPage < totalPages,
                    actualPage > 1);
        })
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** 错误详情（含完整堆栈） */
    public Mono<AdminErrorResponse> getError(String id) {
        return Mono.fromCallable(() -> adminMapper.findErrorById(id))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** 按 requestId 过滤错误列表 */
    public Mono<List<AdminErrorResponse>> listErrorsByRequestId(String requestId) {
        return Mono.fromCallable(() -> adminMapper.listErrorsByRequestId(requestId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** requestId 对应的请求链路（trace + spans） */
    public Mono<AdminTraceDetailResponse> getTraceDetail(String requestId) {
        return Mono.fromCallable(() -> {
                    AdminTraceDetailResponse detail = new AdminTraceDetailResponse();
                    detail.setTrace(adminMapper.findTraceByRequestId(requestId));
                    detail.setSpans(adminMapper.listSpansByRequestId(requestId));
                    return detail;
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** 用户列表（分页，含活跃时间与用量聚合） */
    public Mono<AdminPageResponse<AdminUserResponse>> listUsers(int page, int size) {
        return Mono.fromCallable(() -> {
                    int normalizedSize = normalizePageSize(size);
                    int requestedPage = Math.max(page, 1);
                    long total = adminMapper.countUsersForPage();
                    int totalPages = total == 0
                            ? 0
                            : (int) ((total + normalizedSize - 1) / normalizedSize);
                    int actualPage = totalPages == 0 ? 1 : Math.min(requestedPage, totalPages);
                    int offset = (actualPage - 1) * normalizedSize;
                    return new AdminPageResponse<>(
                            adminMapper.listUsers(offset, normalizedSize),
                            actualPage,
                            normalizedSize,
                            total,
                            totalPages,
                            totalPages > 0 && actualPage < totalPages,
                            actualPage > 1);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** 反馈分页；status 传 null 或空 = 不筛选 */
    public Mono<AdminPageResponse<AdminFeedbackResponse>> listFeedbackPage(int page, int size, String status) {
        return Mono.fromCallable(() -> {
                    String normalizedStatus = (status == null || status.isBlank()) ? null : status;
                    int normalizedSize = normalizePageSize(size);
                    int requestedPage = Math.max(page, 1);
                    long total = adminMapper.countFeedbackByStatus(normalizedStatus);
                    int totalPages = total == 0
                            ? 0
                            : (int) ((total + normalizedSize - 1) / normalizedSize);
                    int actualPage = totalPages == 0 ? 1 : Math.min(requestedPage, totalPages);
                    int offset = (actualPage - 1) * normalizedSize;
                    return new AdminPageResponse<>(
                            adminMapper.listFeedbackPage(normalizedStatus, offset, normalizedSize),
                            actualPage,
                            normalizedSize,
                            total,
                            totalPages,
                            totalPages > 0 && actualPage < totalPages,
                            actualPage > 1);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** 反馈状态流转；返回更新行数（0 = 反馈不存在） */
    public Mono<Integer> updateFeedbackStatus(String id, String status) {
        return Mono.fromCallable(() -> adminMapper.updateFeedbackStatus(id, status))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private int normalizeLimit(int limit) {
        return Math.max(1, Math.min(limit, 200));
    }

    private int normalizePageSize(int size) {
        return Math.max(1, Math.min(size, 100));
    }
}
