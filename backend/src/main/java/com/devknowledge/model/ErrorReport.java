package com.devknowledge.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * 脱敏错误上报记录。
 */
@Data
@TableName("error_reports")
public class ErrorReport {

    @TableId(type = IdType.INPUT)
    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID id;

    private String requestId;

    @TableField(typeHandler = UuidTypeHandler.class)
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
