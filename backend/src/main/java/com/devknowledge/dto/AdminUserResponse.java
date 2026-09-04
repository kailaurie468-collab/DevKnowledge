package com.devknowledge.dto;

import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * 后台用户列表行。
 */
@Data
public class AdminUserResponse {
    private UUID id;
    private String email;
    private String displayName;
    private Instant createdAt;
    private Instant lastActiveAt;
    private Long totalTokens;
    private Long demoCount;
    private Long feedbackCount;
}
