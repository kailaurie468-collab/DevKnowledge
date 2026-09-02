package com.devknowledge.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SensitiveDataSanitizer 错误摘要脱敏")
class SensitiveDataSanitizerTest {

    @Test
    @DisplayName("清理 Bearer、API Key 和密码")
    void sanitizesCredentials() {
        String result = SensitiveDataSanitizer.sanitize(
                "Authorization: Bearer abc.def.ghi api_key=secret-value password:pwd123");

        assertThat(result).doesNotContain("abc.def.ghi", "secret-value", "pwd123");
        assertThat(result).contains("[REDACTED]");
    }

    @Test
    @DisplayName("限制摘要长度")
    void limitsLength() {
        String result = SensitiveDataSanitizer.sanitize("x".repeat(3000));

        assertThat(result).hasSize(2003);
        assertThat(result).endsWith("...");
    }
}
