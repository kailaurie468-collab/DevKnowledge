package com.devknowledge.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MarkdownChunker - flexmark AST 切分")
class MarkdownChunkerTest {

    private MarkdownChunker chunker;

    @BeforeEach
    void setUp() {
        chunker = new MarkdownChunker();
    }

    // ==================== 边界条件 ====================

    @Nested
    @DisplayName("边界条件")
    class EdgeCases {

        @Test
        @DisplayName("null 输入返回空列表")
        void nullInput() {
            assertThat(chunker.split(null, null, null)).isEmpty();
        }

        @Test
        @DisplayName("空字符串返回空列表")
        void emptyInput() {
            assertThat(chunker.split("", null, null)).isEmpty();
        }

        @Test
        @DisplayName("纯空白返回空列表")
        void blankInput() {
            assertThat(chunker.split("   \n\n  ", null, null)).isEmpty();
        }

        @Test
        @DisplayName("纯文本无标题返回单个 chunk")
        void plainTextNoHeading() {
            String content = "Plain text without heading structure for testing chunker behavior.";
            List<String> chunks = chunker.split(content, null, null);
            assertThat(chunks).hasSize(1);
            assertThat(chunks.get(0)).contains("Plain text");
        }
    }

    // ==================== 标题切分 ====================

    @Nested
    @DisplayName("标题切分")
    class HeadingSplit {

        @Test
        @DisplayName("按 h1 标题切分：内容超过 maxSize 时分为独立 chunk")
        void splitByH1() {
            // 每段内容足够长，使得合并后超过 maxSize=80
            String content = "# Chapter One\n\n" + "A".repeat(60) + "\n\n" +
                    "# Chapter Two\n\n" + "B".repeat(60);
            List<String> chunks = chunker.split(content, 20, 80);
            // 两段各 73 字符，合并后 148 > 80，应分为 2 个 chunk
            assertThat(chunks).hasSize(2);
            assertThat(chunks.get(0)).contains("# Chapter One");
            assertThat(chunks.get(1)).contains("# Chapter Two");
        }

        @Test
        @DisplayName("短段落自动合并（合并后不超过 maxSize）")
        void mergeShortSections() {
            String content = "# Section A\n\nShort content.\n\n" +
                    "# Section B\n\nAlso short content.";
            // maxSize=1000，两段合并后远小于 1000，应合并为 1 个 chunk
            List<String> chunks = chunker.split(content, 20, 1000);
            assertThat(chunks).hasSize(1);
        }

        @Test
        @DisplayName("多级标题（h1/h2/h3）每个标题创建独立 section")
        void splitByMultiLevelHeadings() {
            // 每个标题都会触发新 section，无论层级
            String sub1 = "X".repeat(60);
            String sub2 = "Y".repeat(60);
            String content = "# Overview\n\n" + sub1 + "\n\n" +
                    "## Background\n\nDetails here.\n\n" +
                    "# Implementation\n\n" + sub2;
            List<String> chunks = chunker.split(content, 20, 80);
            // 3 个标题 = 3 个 section，各自独立
            assertThat(chunks).hasSize(3);
            assertThat(chunks.get(0)).contains("# Overview");
            assertThat(chunks.get(1)).contains("## Background");
            assertThat(chunks.get(2)).contains("# Implementation");
        }
    }

    // ==================== 代码块保留 ====================

    @Nested
    @DisplayName("代码块保留")
    class CodeBlockPreservation {

        @Test
        @DisplayName("围栏代码块完整保留在同一 chunk")
        void fencedCodeBlockPreserved() {
            String content = "# Example\n\nHere is Java code:\n\n" +
                    "```java\npublic class Hello {\n    System.out.println(\"Hello\");\n}\n```\n\n" +
                    "Text after code block.";
            List<String> chunks = chunker.split(content, 20, 1000);
            String allContent = String.join("\n", chunks);
            assertThat(allContent).contains("```java");
            assertThat(allContent).contains("Hello");
        }

        @Test
        @DisplayName("带语言标识的代码块保留语言标记")
        void codeBlockWithLanguage() {
            String content = "# Config\n\n```yaml\nserver:\n  port: 8080\n```";
            List<String> chunks = chunker.split(content, 20, 1000);
            String allContent = String.join("\n", chunks);
            assertThat(allContent).contains("```yaml");
        }
    }

    // ==================== 合并与拆分 ====================

    @Nested
    @DisplayName("合并与拆分")
    class MergeAndSplit {

        @Test
        @DisplayName("超长段落按 maxSize 拆分")
        void splitLongSection() {
            StringBuilder sb = new StringBuilder("# Title\n\n");
            for (int i = 0; i < 50; i++) {
                sb.append("This is paragraph ").append(i + 1).append(" for testing long text splitting. ");
            }
            List<String> chunks = chunker.split(sb.toString(), 50, 300);
            assertThat(chunks.size()).isGreaterThan(1);
            for (String chunk : chunks) {
                assertThat(chunk.length()).isLessThanOrEqualTo(400);
            }
        }

        @Test
        @DisplayName("自定义 minSize 和 maxSize 参数生效")
        void customSizeParams() {
            // 每段 60 字符，maxSize=70 无法合并两段（60+2+60=122 > 70）
            String content = "# Section One\n\n" + "A".repeat(60) + "\n\n" +
                    "# Section Two\n\n" + "B".repeat(60);
            List<String> chunks = chunker.split(content, 20, 70);
            assertThat(chunks.size()).isGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("最后一个 chunk 太小时尝试与前一个合并")
        void mergeSmallLastChunk() {
            // 第一段足够长，最后一段很短
            String content = "# Big\n\n" + "X".repeat(200) + "\n\n" +
                    "# Tiny\n\nShort.";
            List<String> chunks = chunker.split(content, 50, 300);
            // 最后一个短 chunk 应与前一个合并
            assertThat(chunks).hasSize(1);
        }
    }

    // ==================== 中文断行 ====================

    @Nested
    @DisplayName("中文断行")
    class ChineseLineBreak {

        @Test
        @DisplayName("超长段落在标点处断行")
        void breakAtPunctuation() {
            StringBuilder sb = new StringBuilder("# Title\n\n");
            for (int i = 0; i < 20; i++) {
                sb.append("This is sentence one. This is sentence two. This is sentence three. ");
            }
            List<String> chunks = chunker.split(sb.toString(), 50, 200);
            assertThat(chunks.size()).isGreaterThan(1);
        }
    }
}
