package com.devknowledge.service.ai;

import com.devknowledge.dto.KbChunkSearchResult;
import com.devknowledge.model.KnowledgeBase;
import com.devknowledge.service.KbService;
import com.devknowledge.service.KnowledgeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Demo 生成工具提供者
 * 将工具定义（AiFunction）和执行逻辑（ToolHandler）封装在一起，
 * 按需组装工具列表，避免 DemoService 中堆砌静态字段。
 */
@Component
public class DemoToolProvider {

    private static final Logger log = LoggerFactory.getLogger(DemoToolProvider.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final KnowledgeService knowledgeService;
    private final KbService kbService;

    public DemoToolProvider(KnowledgeService knowledgeService, KbService kbService) {
        this.knowledgeService = knowledgeService;
        this.kbService = kbService;
    }

    // ==================== 工具定义 ====================

    /** 搜索公共知识链接 */
    private static final AiFunction SEARCH_LINKS = new AiFunction(
            "search_links",
            "搜索框架文档链接，获取官方文档和最佳实践",
            """
            {
                "type": "object",
                "properties": {
                    "query": { "type": "string", "description": "搜索关键词，如 'useEffect' 或 '表单验证'" }
                },
                "required": ["query"]
            }
            """
    );

    /** 获取框架信息 */
    private static final AiFunction GET_FRAMEWORK_INFO = new AiFunction(
            "get_framework_info",
            "获取指定框架的基本信息和官方文档地址",
            """
            {
                "type": "object",
                "properties": {
                    "slug": { "type": "string", "description": "框架标识，如 'react'、'vue'、'spring-boot'" }
                },
                "required": ["slug"]
            }
            """
    );

    /** 搜索用户知识库 */
    private static final AiFunction SEARCH_KB = new AiFunction(
            "search_kb",
            "搜索用户知识库中的文档内容，获取私有知识和参考资料",
            """
            {
                "type": "object",
                "properties": {
                    "query": { "type": "string", "description": "搜索关键词" }
                },
                "required": ["query"]
            }
            """
    );

    // ==================== 工具组装 ====================

    /**
     * 构建基础工具列表（始终包含）
     * 已移除 search_links 和 get_framework_info，Demo 生成不需要这些工具
     */
    public List<AiFunction> getBaseTools() {
        return List.of();
    }

    /**
     * 构建基础工具处理器
     */
    public Map<String, ToolHandler> getBaseHandlers() {
        return new HashMap<>();
    }

    /**
     * 构建知识库工具（可选，用户选择知识库时才注入）
     */
    public AiFunction getKbTool() {
        return SEARCH_KB;
    }

    /**
     * 构建知识库工具处理器
     */
    public ToolHandler getKbHandler(UUID userId, UUID kbId) {
        return buildSearchKbHandler(userId, kbId);
    }

    // ==================== Handler 实现 ====================

    private ToolHandler buildSearchLinksHandler() {
        return args -> {
            try {
                String query = extractJsonString(args, "query");
                log.info("工具 search_links 执行，queryLength={}", query != null ? query.length() : 0);
                var results = knowledgeService.searchLinks(query).block();
                if (results == null || results.isEmpty()) return "未找到相关文档";

                StringBuilder sb = new StringBuilder();
                for (var r : results) {
                    sb.append("- ").append(r.getLink().getTitle())
                            .append(": ").append(r.getLink().getUrl());
                    if (r.getLink().getDescription() != null) {
                        sb.append(" (").append(r.getLink().getDescription()).append(")");
                    }
                    sb.append("\n");
                }
                return sb.toString();
            } catch (Exception e) {
                return "搜索失败: " + e.getMessage();
            }
        };
    }

    private ToolHandler buildGetFrameworkInfoHandler() {
        return args -> {
            try {
                String slug = extractJsonString(args, "slug");
                log.info("工具 get_framework_info 执行，slug={}", slug);
                var results = knowledgeService.searchLinks(slug).block();
                if (results == null || results.isEmpty()) return "未找到框架: " + slug;

                StringBuilder sb = new StringBuilder();
                for (var r : results) {
                    sb.append("- ").append(r.getLink().getTitle())
                            .append(": ").append(r.getLink().getUrl());
                    if (r.getLink().getDescription() != null) {
                        sb.append(" (").append(r.getLink().getDescription()).append(")");
                    }
                    sb.append("\n");
                }
                return sb.toString();
            } catch (Exception e) {
                return "获取框架信息失败: " + e.getMessage();
            }
        };
    }
    private ToolHandler buildSearchKbHandler(UUID userId, UUID kbId) {
        return args -> {
            try {
                String query = extractJsonString(args, "query");
                log.info("工具 search_kb 执行，kbId={}, queryLength={}", kbId, query != null ? query.length() : 0);

                var results = kbService.searchKbVector(userId, kbId, query, 5).block();
                if (results == null || results.isEmpty()) return "知识库中未找到相关内容";

                StringBuilder sb = new StringBuilder();
                for (var chunk : results) {
                    sb.append("【").append(chunk.getFilename() != null ? chunk.getFilename() : "文档").append("】");
                    sb.append(" 相关度: ").append(String.format("%.0f%%", chunk.getScore() * 100)).append("\n");
                    sb.append(chunk.getContent()).append("\n\n");
                }
                return sb.toString();
            } catch (Exception e) {
                return "知识库搜索失败: " + e.getMessage();
            }
        };
    }

    // ==================== 工具方法 ====================

    private String extractJsonString(String json, String field) {
        try {
            return objectMapper.readTree(json).has(field)
                    ? objectMapper.readTree(json).get(field).asText()
                    : "";
        } catch (Exception e) {
            return "";
        }
    }
}
