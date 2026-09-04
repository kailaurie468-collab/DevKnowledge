package com.devknowledge.dto;

import lombok.Data;

import java.util.List;

/**
 * requestId 对应的完整请求链路（trace + spans）。
 */
@Data
public class AdminTraceDetailResponse {
    private AdminRequestTraceResponse trace;
    private List<AdminSpanResponse> spans;
}
