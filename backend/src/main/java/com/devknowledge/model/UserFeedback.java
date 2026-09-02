package com.devknowledge.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * 用户主动提交的意见反馈。
 */
@Data
@TableName("user_feedback")
public class UserFeedback {

    @TableId(type = IdType.INPUT)
    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID id;

    private String requestId;

    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID userId;

    private String feedbackType;
    private String content;
    private String contact;
    private String page;
    private String status;
    private Instant createdAt;
}
