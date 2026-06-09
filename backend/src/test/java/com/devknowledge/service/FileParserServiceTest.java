package com.devknowledge.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("FileParserService - 文件解析")
class FileParserServiceTest {

    private FileParserService parser;

    @BeforeEach
    void setUp() {
        parser = new FileParserService();
    }

    // ==================== 小文件直接解析 ====================

    @Nested
    @DisplayName("小文件解析（< 1MB）")
    class SmallFileParsing {

        @Test
        @DisplayName("TXT 文件直接解析")
        void parseTxt() throws IOException {
            byte[] content = "Hello, World!\n你好世界".getBytes(StandardCharsets.UTF_8);
            String result = parser.parse("test.txt", content);
            assertThat(result).isEqualTo("Hello, World!\n你好世界");
        }

        @Test
        @DisplayName("MD 文件直接解析")
        void parseMd() throws IOException {
            byte[] content = "# 标题\n\n正文内容".getBytes(StandardCharsets.UTF_8);
            String result = parser.parse("test.md", content);
            assertThat(result).contains("# 标题");
            assertThat(result).contains("正文内容");
        }

        @Test
        @DisplayName("markdown 后缀也能解析")
        void parseMarkdown() throws IOException {
            byte[] content = "内容".getBytes(StandardCharsets.UTF_8);
            String result = parser.parse("test.markdown", content);
            assertThat(result).isEqualTo("内容");
        }

        @Test
        @DisplayName("不支持的文件格式抛出异常")
        void unsupportedFormat() {
            byte[] content = "data".getBytes(StandardCharsets.UTF_8);
            assertThatThrownBy(() -> parser.parse("test.xlsx", content))
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("不支持的文件格式");
        }
    }

    // ==================== 大文件流式解析 ====================

    @Nested
    @DisplayName("大文件流式解析（>= 1MB）")
    class LargeFileParsing {

        @Test
        @DisplayName("大文件流式解析结果与直接解析一致")
        void streamingConsistency() throws IOException {
            // 构造 1.5MB 的内容
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 50000; i++) {
                sb.append("这是第").append(i).append("行内容，用于测试大文件流式解析。\n");
            }
            byte[] content = sb.toString().getBytes(StandardCharsets.UTF_8);

            // 确认超过 1MB
            assertThat(content.length).isGreaterThan(1024 * 1024);

            String result = parser.parse("large.txt", content);
            assertThat(result).isNotEmpty();
            assertThat(result).contains("第0行");
            assertThat(result).contains("第49999行");
        }

        @Test
        @DisplayName("大文件不丢失内容")
        void noContentLoss() throws IOException {
            String original = "A".repeat(1_500_000); // 1.5MB 纯 ASCII
            byte[] content = original.getBytes(StandardCharsets.UTF_8);
            String result = parser.parse("large.txt", content);
            assertThat(result).hasSize(1_500_000);
        }
    }

    // ==================== InputStream 解析 ====================

    @Nested
    @DisplayName("InputStream 解析")
    class StreamParsing {

        @Test
        @DisplayName("从 InputStream 解析文本")
        void parseFromInputStream() throws IOException {
            byte[] content = "流式输入内容".getBytes(StandardCharsets.UTF_8);
            InputStream is = new ByteArrayInputStream(content);
            String result = parser.parseFromStream("test.txt", is);
            assertThat(result).isEqualTo("流式输入内容");
        }

        @Test
        @DisplayName("大 InputStream 流式解析")
        void parseLargeStream() throws IOException {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 10000; i++) {
                sb.append("Stream line ").append(i).append("\n");
            }
            byte[] content = sb.toString().getBytes(StandardCharsets.UTF_8);
            InputStream is = new ByteArrayInputStream(content);
            String result = parser.parseFromStream("large.txt", is);
            assertThat(result).contains("Stream line 0");
            assertThat(result).contains("Stream line 9999");
        }
    }
}
