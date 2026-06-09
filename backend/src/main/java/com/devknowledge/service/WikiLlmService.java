package com.devknowledge.service;

import com.devknowledge.model.UserAiConfig;
import com.devknowledge.service.ai.AiProviderAdapter;
import com.devknowledge.service.ai.AiProviderFactory;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.*;

/**
 * Wiki LLM 分析服务
 * 负责调用 LLM 进行实体提取、关系识别、页面生成
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WikiLlmService {

    private final AiProviderFactory aiProviderFactory;
    private final AiConfigService aiConfigService;
    private final ObjectMapper objectMapper;

    /**
     * 基础分析：生成文档摘要
     */
    public Mono<String> generateSummary(UUID userId, String filename, String content) {
        String prompt = """
                请为以下文档生成一段简洁的摘要（200-300字），提取核心要点。

                文档名称: %s

                文档内容:
                %s

                请直接输出摘要内容，不要添加额外说明。
                """.formatted(filename, truncate(content, 3000));

        return callLlm(userId, prompt);
    }

    /**
     * 深度分析：提取实体和关系
     */
    public Mono<AnalysisResult> analyzeEntities(UUID userId, String content, String filename) {
        String prompt = """
                分析以下文档，提取其中的关键实体和它们之间的关系。

                文档名称: %s

                文档内容:
                %s

                请以 JSON 格式输出，格式如下:
                {
                  "entities": [
                    {
                      "name": "实体名称",
                      "type": "concept/framework/api/tool",
                      "description": "简要描述（50字以内）"
                    }
                  ],
                  "relations": [
                    {
                      "source": "源实体名称",
                      "target": "目标实体名称",
                      "relation": "uses/extends/contradicts/related_to",
                      "description": "关系描述",
                      "strength": 0.8
                    }
                  ],
                  "summary": "文档整体摘要（200字以内）"
                }

                注意:
                1. 实体名称使用英文小写，用 - 连接（如 react, virtual-dom）
                2. 只提取有意义的核心实体，不要过于细碎
                3. strength 范围 0.0-1.0，表示关系的紧密程度
                4. 只输出 JSON，不要其他内容
                """.formatted(filename, truncate(content, 5000));

        return callLlm(userId, prompt)
                .flatMap(response -> {
                    try {
                        String json = extractJson(response);
                        Map<String, Object> result = objectMapper.readValue(json, new TypeReference<>() {});

                        AnalysisResult analysis = new AnalysisResult();
                        analysis.setSummary((String) result.getOrDefault("summary", ""));

                        // 解析实体
                        List<Map<String, Object>> entitiesRaw = (List<Map<String, Object>>) result.getOrDefault("entities", List.of());
                        List<EntityInfo> entities = new ArrayList<>();
                        for (Map<String, Object> e : entitiesRaw) {
                            EntityInfo info = new EntityInfo();
                            info.setName((String) e.get("name"));
                            info.setType((String) e.getOrDefault("type", "concept"));
                            info.setDescription((String) e.getOrDefault("description", ""));
                            entities.add(info);
                        }
                        analysis.setEntities(entities);

                        // 解析关系
                        List<Map<String, Object>> relationsRaw = (List<Map<String, Object>>) result.getOrDefault("relations", List.of());
                        List<RelationInfo> relations = new ArrayList<>();
                        for (Map<String, Object> r : relationsRaw) {
                            RelationInfo info = new RelationInfo();
                            info.setSource((String) r.get("source"));
                            info.setTarget((String) r.get("target"));
                            info.setRelation((String) r.getOrDefault("relation", "related_to"));
                            info.setDescription((String) r.getOrDefault("description", ""));
                            info.setStrength(r.get("strength") instanceof Number
                                    ? ((Number) r.get("strength")).doubleValue() : 0.5);
                            relations.add(info);
                        }
                        analysis.setRelations(relations);

                        return Mono.just(analysis);
                    } catch (Exception e) {
                        log.error("解析 LLM 分析结果失败: {}", e.getMessage());
                        AnalysisResult fallback = new AnalysisResult();
                        fallback.setSummary("分析失败，请重试");
                        fallback.setEntities(List.of());
                        fallback.setRelations(List.of());
                        return Mono.just(fallback);
                    }
                });
    }

    /**
     * 生成 wiki 实体页面
     */
    public Mono<String> generateWikiPage(UUID userId, String entityName, String entityType,
                                          String description, String sourceContent) {
        String prompt = """
                为以下实体生成一个 wiki 页面（markdown 格式）。

                实体名称: %s
                实体类型: %s
                描述: %s

                相关源内容:
                %s

                请生成一个结构化的 wiki 页面，包含:
                1. YAML frontmatter（type, category, tags, created）
                2. 标题和简介
                3. 核心概念或要点
                4. 使用 [[实体名]] 格式标注相关实体链接
                5. 来源引用

                直接输出 markdown 内容。
                """.formatted(entityName, entityType, description, truncate(sourceContent, 2000));

        return callLlm(userId, prompt);
    }

    /**
     * Lint 健康检查：检测矛盾、孤立页面、缺失链接
     */
    public Mono<LintResult> lintWiki(UUID userId, String pagesSummary, String entitiesSummary) {
        String prompt = """
                对以下 Wiki 知识库进行健康检查。

                页面列表:
                %s

                实体列表:
                %s

                请检测以下问题并以 JSON 格式输出:
                {
                  "contradictions": ["矛盾描述1", "矛盾描述2"],
                  "orphanPages": ["孤立页面1", "孤立页面2"],
                  "missingLinks": [
                    {"from": "页面A", "to": "页面B", "reason": "应该建立链接的原因"}
                  ],
                  "suggestions": ["建议1", "建议2"]
                }

                只输出 JSON，不要其他内容。
                """.formatted(pagesSummary, entitiesSummary);

        return callLlm(userId, prompt)
                .flatMap(response -> {
                    try {
                        String json = extractJson(response);
                        Map<String, Object> result = objectMapper.readValue(json, new TypeReference<>() {});
                        LintResult lint = new LintResult();
                        lint.setContradictions((List<String>) result.getOrDefault("contradictions", List.of()));
                        lint.setOrphanPages((List<String>) result.getOrDefault("orphanPages", List.of()));
                        lint.setSuggestions((List<String>) result.getOrDefault("suggestions", List.of()));
                        return Mono.just(lint);
                    } catch (Exception e) {
                        LintResult fallback = new LintResult();
                        fallback.setSuggestions(List.of("分析失败，请重试"));
                        return Mono.just(fallback);
                    }
                });
    }

    /**
     * 调用 LLM，返回完整文本
     */
    private Mono<String> callLlm(UUID userId, String userMessage) {
        return Mono.fromCallable(() -> {
            UserAiConfig config = aiConfigService.getActiveConfigEntity(userId);
            if (config == null) {
                throw new RuntimeException("请先配置 AI 服务商");
            }
            return config;
        })
        .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
        .flatMap(config -> {
            AiProviderAdapter adapter = aiProviderFactory.getAdapter(config.getProvider());
            return adapter.streamCompletion(
                    "你是知识图谱分析专家，擅长从文档中提取实体和关系。请用中文回答。",
                    userMessage,
                    config
            ).collectList().map(parts -> String.join("", parts));
        });
    }

    private String truncate(String text, int maxLen) {
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }

    private String extractJson(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    // ==================== 内部数据类 ====================

    @lombok.Data
    public static class AnalysisResult {
        private String summary;
        private List<EntityInfo> entities;
        private List<RelationInfo> relations;
    }

    @lombok.Data
    public static class EntityInfo {
        private String name;
        private String type;
        private String description;
    }

    @lombok.Data
    public static class RelationInfo {
        private String source;
        private String target;
        private String relation;
        private String description;
        private Double strength;
    }

    @lombok.Data
    public static class LintResult {
        private List<String> contradictions;
        private List<String> orphanPages;
        private List<Map<String, String>> missingLinks;
        private List<String> suggestions;
    }
}
