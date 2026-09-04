package com.devknowledge.service;

import java.util.regex.Pattern;

/**
 * 错误上报前的敏感信息清洗器。
 * 该类只处理错误摘要，不接收请求正文或 AI 输出。
 */
public final class SensitiveDataSanitizer {

    private static final int MAX_LENGTH = 2000;
    /** 完整堆栈的最大长度，比摘要宽松以保留完整调用链 */
    private static final int MAX_DETAIL_LENGTH = 16000;
    private static final Pattern BEARER_PATTERN =
            Pattern.compile("(?i)\\bBearer\\s+[A-Za-z0-9._~+/=-]+");
    private static final Pattern KEY_VALUE_PATTERN =
            Pattern.compile("(?i)\\b(api[-_ ]?key|password|passwd|secret|authorization|access[-_ ]?token|refresh[-_ ]?token)\\s*[:=]\\s*[^\\s,;]+");
    private static final Pattern JSON_KEY_PATTERN =
            Pattern.compile("(?i)(\"(?:api[-_ ]?key|password|passwd|secret|authorization|access[-_ ]?token|refresh[-_ ]?token)\"\\s*:\\s*\")[^\"]*(\")");

    private SensitiveDataSanitizer() {
    }

    /**
     * 脱敏并限制错误摘要长度，避免把凭证或大段响应内容写入日志/数据库/邮件。
     */
    public static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "未知错误";
        }

        String sanitized = BEARER_PATTERN.matcher(value).replaceAll("Bearer [REDACTED]");
        sanitized = KEY_VALUE_PATTERN.matcher(sanitized).replaceAll("$1=[REDACTED]");
        sanitized = JSON_KEY_PATTERN.matcher(sanitized).replaceAll("$1[REDACTED]$2");
        sanitized = sanitized.replaceAll("\\s+", " ").trim();
        return sanitized.length() <= MAX_LENGTH
                ? sanitized
                : sanitized.substring(0, MAX_LENGTH) + "...";
    }

    /**
     * 脱敏完整堆栈。与 sanitize 的区别：保留换行（堆栈逐行可读）、上限 16000、
     * 空值返回 null（error_detail 列允许 NULL，代表无堆栈）。
     */
    public static String sanitizeDetail(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String sanitized = BEARER_PATTERN.matcher(value).replaceAll("Bearer [REDACTED]");
        sanitized = KEY_VALUE_PATTERN.matcher(sanitized).replaceAll("$1=[REDACTED]");
        sanitized = JSON_KEY_PATTERN.matcher(sanitized).replaceAll("$1[REDACTED]$2");
        return sanitized.length() <= MAX_DETAIL_LENGTH
                ? sanitized
                : sanitized.substring(0, MAX_DETAIL_LENGTH) + "...";
    }
}
