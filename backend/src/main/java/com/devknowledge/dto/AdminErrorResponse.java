package com.devknowledge.dto;

import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * 开发者后台展示的错误摘要。
 */
@Data
public class AdminErrorResponse {

    private UUID id;
    private String requestId;
    private UUID userId;
    private String source;
    private String stage;
    private String errorType;
    private String errorSummary;
    private String method;
    private String path;
    private String page;
    private String appVersion;
    private String userAgent;
    private String environment;
    private Long durationMs;
    private Instant createdAt;
}
