package com.devknowledge.dto;

import lombok.Data;

import java.time.Instant;

/**
 * 开发者后台展示的请求耗时记录。
 */
@Data
public class AdminRequestTraceResponse {

    private String requestId;
    private String method;
    private String path;
    private Integer statusCode;
    private String outcome;
    private Long totalMs;
    private Long firstEventMs;
    private Long firstTextMs;
    private Instant createdAt;
}
