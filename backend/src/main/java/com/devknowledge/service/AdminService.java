package com.devknowledge.service;

import com.devknowledge.dto.AdminErrorResponse;
import com.devknowledge.dto.AdminFeedbackResponse;
import com.devknowledge.dto.AdminOverviewResponse;
import com.devknowledge.dto.AdminPageResponse;
import com.devknowledge.dto.AdminRequestTraceResponse;
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

    public Mono<List<AdminFeedbackResponse>> listFeedback(int limit) {
        return Mono.fromCallable(() -> adminMapper.listFeedback(normalizeLimit(limit)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private int normalizeLimit(int limit) {
        return Math.max(1, Math.min(limit, 200));
    }

    private int normalizePageSize(int size) {
        return Math.max(1, Math.min(size, 100));
    }
}
