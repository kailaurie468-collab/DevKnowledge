package com.devknowledge.service;

import com.vladsch.flexmark.ast.*;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 基于 flexmark-java AST 的 Markdown 文档切分器
 * 按标题层级（h1/h2/h3...）精确切分，合并短块，拆分长块
 */
@Component
public class MarkdownChunker {

    private static final int DEFAULT_MIN_SIZE = 100;
    private static final int DEFAULT_MAX_SIZE = 1000;

    private final Parser parser;

    public MarkdownChunker() {
        MutableDataSet options = new MutableDataSet();
        this.parser = Parser.builder(options).build();
    }

    /**
     * 将 Markdown 内容切分为 chunk 列表
     */
    public List<String> split(String content, Integer minChunkSize, Integer maxChunkSize) {
        if (content == null || content.isBlank()) return List.of();

        int minSize = (minChunkSize != null && minChunkSize >= 20) ? minChunkSize : DEFAULT_MIN_SIZE;
        int maxSize = (maxChunkSize != null && maxChunkSize >= minSize + 50) ? maxChunkSize : DEFAULT_MAX_SIZE;

        // 解析为 AST
        Node document = parser.parse(content);

        // 遍历 AST 顶层节点，按标题拆分为结构块
        List<MarkdownSection> sections = extractSections(document);

        // 合并短块、拆分长块
        return adjustSectionSizes(sections, minSize, maxSize);
    }

    /**
     * 遍历 AST，将文档按标题层级拆分为结构化段落
     */
    private List<MarkdownSection> extractSections(Node document) {
        List<MarkdownSection> sections = new ArrayList<>();
        StringBuilder currentContent = new StringBuilder();
        String currentHeading = null;

        for (Node child = document.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof Heading heading) {
                // 遇到标题：先刷出之前的内容
                if (currentContent.length() > 0) {
                    sections.add(new MarkdownSection(currentHeading, currentContent.toString().trim()));
                    currentContent.setLength(0);
                }
                // 标题行作为新段落的起始
                currentHeading = child.getChars().toString();
                currentContent.append(currentHeading).append("\n");
            } else if (child instanceof FencedCodeBlock codeBlock) {
                // 代码块：原样保留，添加围栏标记
                currentContent.append("```");
                // 获取语言标识
                String info = codeBlock.getInfo().toString();
                if (!info.isEmpty()) {
                    currentContent.append(info);
                }
                currentContent.append("\n");
                currentContent.append(codeBlock.getContentChars().toString());
                currentContent.append("```\n");
            } else if (child instanceof IndentedCodeBlock codeBlock) {
                // 缩进代码块
                currentContent.append(codeBlock.getChars().toString()).append("\n");
            } else if (child instanceof ThematicBreak) {
                // 水平分隔线
                currentContent.append("---\n");
            } else {
                // 其他节点（段落、列表、表格等）：提取文本
                currentContent.append(child.getChars().toString()).append("\n");
            }
        }

        // 刷出最后一个段落
        if (currentContent.length() > 0) {
            sections.add(new MarkdownSection(currentHeading, currentContent.toString().trim()));
        }

        return sections;
    }

    /**
     * 调整段落大小：合并过小的段落，拆分过大的段落
     */
    private List<String> adjustSectionSizes(List<MarkdownSection> sections, int minSize, int maxSize) {
        List<String> result = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();

        for (MarkdownSection section : sections) {
            // 段落过大：先刷出缓冲区，再拆分当前段落
            if (section.content.length() > maxSize) {
                if (buffer.length() > 0) {
                    result.add(buffer.toString().trim());
                    buffer.setLength(0);
                }
                result.addAll(splitLargeSection(section, maxSize));
                continue;
            }

            // 段落可以合并到缓冲区
            if (buffer.length() == 0) {
                buffer.append(section.content);
            } else if (buffer.length() + 2 + section.content.length() <= maxSize) {
                buffer.append("\n\n").append(section.content);
            } else {
                // 缓冲区已满，先输出再开始新段落
                result.add(buffer.toString().trim());
                buffer.setLength(0);
                buffer.append(section.content);
            }
        }

        // 刷出最后的缓冲区
        if (buffer.length() > 0) {
            String lastChunk = buffer.toString().trim();
            if (!lastChunk.isEmpty()) {
                // 如果最后一个块太小，尝试与前一个合并
                if (lastChunk.length() < minSize && !result.isEmpty()) {
                    String prev = result.get(result.size() - 1);
                    if (prev.length() + 2 + lastChunk.length() <= maxSize) {
                        result.set(result.size() - 1, prev + "\n\n" + lastChunk);
                    } else {
                        result.add(lastChunk);
                    }
                } else {
                    result.add(lastChunk);
                }
            }
        }

        return result;
    }

    /**
     * 拆分过大的段落
     */
    private List<String> splitLargeSection(MarkdownSection section, int maxSize) {
        List<String> result = new ArrayList<>();
        String[] lines = section.content.split("\n", -1);
        StringBuilder buffer = new StringBuilder();

        for (String line : lines) {
            // 单行超长：先刷出缓冲区，再按行拆分
            if (line.length() > maxSize) {
                if (buffer.length() > 0) {
                    result.add(buffer.toString().trim());
                    buffer.setLength(0);
                }
                result.addAll(splitLongLine(line, maxSize));
                continue;
            }

            if (buffer.length() + line.length() + 1 > maxSize && buffer.length() > 0) {
                result.add(buffer.toString().trim());
                buffer.setLength(0);
            }
            if (buffer.length() > 0) buffer.append("\n");
            buffer.append(line);
        }

        if (buffer.length() > 0) {
            result.add(buffer.toString().trim());
        }

        return result;
    }

    /**
     * 拆分超长行（中文按句号拆分，英文按空格拆分）
     */
    private List<String> splitLongLine(String line, int maxSize) {
        List<String> result = new ArrayList<>();
        int start = 0;
        while (start < line.length()) {
            int end = Math.min(start + maxSize, line.length());
            if (end < line.length()) {
                // 优先在句号处断行
                int breakAt = findBreakPoint(line, start, end);
                if (breakAt > start) end = breakAt;
            }
            result.add(line.substring(start, end));
            start = end;
        }
        return result;
    }

    /**
     * 寻找合适的断行点（句号 > 逗号 > 空格）
     */
    private int findBreakPoint(String text, int start, int end) {
        // 从后往前找断行点
        for (int i = end - 1; i > start + (end - start) / 3; i--) {
            char c = text.charAt(i);
            if (c == '。' || c == '.' || c == '！' || c == '？' || c == '\n') {
                return i + 1;
            }
        }
        for (int i = end - 1; i > start + (end - start) / 3; i--) {
            char c = text.charAt(i);
            if (c == '，' || c == ',' || c == '；' || c == ';' || c == ' ') {
                return i + 1;
            }
        }
        return end;
    }

    /**
     * 内部数据结构：Markdown 段落
     */
    private record MarkdownSection(String heading, String content) {}
}
