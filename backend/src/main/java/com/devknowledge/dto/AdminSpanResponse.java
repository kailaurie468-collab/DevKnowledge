package com.devknowledge.dto;

import lombok.Data;

import java.time.Instant;

/**
 * 请求阶段耗时（链路追溯用）。
 */
@Data
public class AdminSpanResponse {
    private String stage;
    private String status;
    private Long durationMs;
    private Instant createdAt;
}
