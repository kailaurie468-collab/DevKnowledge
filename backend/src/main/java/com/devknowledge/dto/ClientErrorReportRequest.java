package com.devknowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 前端错误上报请求。
 * 只接受脱敏摘要和运行环境元数据，不接受请求正文或 AI 输出。
 */
@Data
public class ClientErrorReportRequest {

    @Size(max = 64)
    private String requestId;

    @NotBlank
    @Size(max = 2000)
    private String errorSummary;

    @Size(max = 128)
    private String errorType;

    @Size(max = 64)
    private String stage;

    @Size(max = 512)
    private String page;

    @Size(max = 128)
    private String appVersion;

    @Size(max = 512)
    private String userAgent;

    @Size(max = 64)
    private String environment;

    private Long durationMs;
}
