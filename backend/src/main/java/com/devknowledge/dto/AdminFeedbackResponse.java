package com.devknowledge.dto;

import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * 开发者后台展示的用户反馈。
 */
@Data
public class AdminFeedbackResponse {

    private UUID id;
    private String requestId;
    private UUID userId;
    private String feedbackType;
    private String content;
    private String contact;
    private String page;
    private String status;
    private Instant createdAt;
}
