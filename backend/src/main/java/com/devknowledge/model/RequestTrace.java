package com.devknowledge.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * HTTP 请求级可观测性记录。
 * 只保存请求元数据、耗时和脱敏错误摘要，不保存业务正文。
 */
@Data
@TableName("request_traces")
public class RequestTrace {

    @TableId(type = IdType.INPUT)
    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID id;

    private String requestId;

    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID userId;

    private String method;
    private String path;
    private Integer statusCode;
    private String outcome;
    private Long totalMs;
    private Long firstEventMs;
    private Long firstTextMs;
    private String errorCode;
    private String errorSummary;
    private String userAgent;
    private String clientVersion;
    private Instant createdAt;
}
