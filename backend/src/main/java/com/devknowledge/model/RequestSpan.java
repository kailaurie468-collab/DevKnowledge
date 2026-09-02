package com.devknowledge.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * 请求内关键业务阶段的耗时记录。
 */
@Data
@TableName("request_spans")
public class RequestSpan {

    @TableId(type = IdType.INPUT)
    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID id;

    private String requestId;
    private String stage;
    private String status;
    private Long durationMs;
    private Instant createdAt;
}
