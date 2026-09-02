package com.devknowledge.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 开发者后台概览指标。
 */
@Data
@AllArgsConstructor
public class AdminOverviewResponse {

    private long totalUsers;
    private long totalTokens;
    private long totalRequests;
    private long successfulRequests;
    private double successRate;
    private double averageLatencyMs;
    private double p95LatencyMs;
    private long errorCount;
    private long feedbackCount;
}
