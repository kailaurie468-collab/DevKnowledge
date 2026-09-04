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

    @Test
    @DisplayName("sanitizeDetail 保留堆栈换行并清理凭证")
    void sanitizeDetailKeepsNewlines() {
        String stack = "java.lang.IllegalStateException: Bearer abc.def.ghi\n"
                + "\tat com.devknowledge.service.DemoService.generate(DemoService.java:97)\n"
                + "\tat java.base/java.lang.Thread.run(Thread.java:833)";

        String result = SensitiveDataSanitizer.sanitizeDetail(stack);

        assertThat(result).contains("\n");
        assertThat(result).doesNotContain("abc.def.ghi");
        assertThat(result).contains("[REDACTED]");
    }

    @Test
    @DisplayName("sanitizeDetail 截断到 16000 且空值返回 null")
    void sanitizeDetailLimitsLengthAndNull() {
        assertThat(SensitiveDataSanitizer.sanitizeDetail(null)).isNull();
        assertThat(SensitiveDataSanitizer.sanitizeDetail("x".repeat(20000))).hasSize(16003);
    }
}
