package com.devknowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huaban.analysis.jieba.JiebaSegmenter;
import com.huaban.analysis.jieba.SegToken;
import com.devknowledge.dto.AiConfigResponse;
import com.devknowledge.dto.GenerateDemoRequest;
import com.devknowledge.mapper.DemoMapper;
import com.devknowledge.mapper.FrameworkMapper;
import com.devknowledge.mapper.UserAiConfigMapper;
import com.devknowledge.model.Demo;
import com.devknowledge.model.Framework;
import com.devknowledge.model.UserAiConfig;
import com.devknowledge.security.AesUtil;
import com.devknowledge.service.ai.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Demo 生成服务
 * 使用 ReAct Agent 调用用户配置的 AI，支持工具调用和多轮推理
 */
@Service
@RequiredArgsConstructor
public class DemoService {

    private static final Logger log = LoggerFactory.getLogger(DemoService.class);

    private final ReActAgent reactAgent;
    private final UserAiConfigMapper aiConfigMapper;
    private final AiConfigService aiConfigService;
    private final DemoMapper demoMapper;
    private final FrameworkMapper frameworkMapper;
    private final KnowledgeService knowledgeService;

    @Value("${jwt.secret}")
    private String aesSecret;

    // ==================== ReAct 工具定义 ====================

    /** 搜索知识库 */
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

    // ==================== 核心方法 ====================

    /**
     * 检查用户是否已配置 AI 服务商（同步版本，用于前置检查）
     *
     * @param userId 用户 ID
     * @return true = 已配置，false = 未配置
     */
    public boolean hasAiConfigSync(UUID userId) {
        Long count = aiConfigMapper.selectCount(
                new LambdaQueryWrapper<UserAiConfig>().eq(UserAiConfig::getUserId, userId));
        return count > 0;
    }

    /**
     * 使用 ReAct Agent 流式生成 Demo
     * AI 可以自主决定调用 search_links 或 get_framework_info 工具
     *
     * @param userId 当前用户 ID
     * @param req    生成请求
     * @return SSE 事件流（包含 thought / tool_call / text / done / error）
     */
    public Flux<ServerSentEvent<String>> generateDemo(UUID userId, GenerateDemoRequest req) {
        // 用 Flux.defer 延迟执行，确保错误通过 SSE 事件返回而非 HTTP 错误
        return Flux.defer(() -> {
            // 1. 加载用户 AI 配置
            if (userId == null) {
                return Flux.just(ServerSentEvent.<String>builder()
                        .data("[ERROR]请先登录并配置 AI 服务商").build());
            }
            UserAiConfig config = aiConfigService.getActiveConfigEntity(userId);
            if (config == null) {
                return Flux.just(ServerSentEvent.<String>builder()
                        .data("[ERROR]请先在设置页配置 AI 服务商").build());
            }

            AesUtil aes = new AesUtil(aesSecret);
            config.setApiKey(aes.decrypt(config.getApiKey()));

            // 2. 构建系统提示词和工具
            String systemPrompt = buildSystemPrompt(req);
            List<AiFunction> tools = List.of(SEARCH_LINKS, GET_FRAMEWORK_INFO);
            Map<String, ToolHandler> handlers = buildToolHandlers();

            // 3. 运行 ReAct Agent，收集输出并保存
            StringBuilder outputCollector = new StringBuilder();

            int maxIter = req.getMaxIterations() != null ? req.getMaxIterations() : 5;
            return reactAgent.run(systemPrompt, req.getPrompt(), tools, handlers, config, maxIter)
                    .map(chunk -> {
                        // 收集文本输出
                        if (chunk.getType() == AiChunkType.TEXT && chunk.getContent() != null) {
                            outputCollector.append(chunk.getContent());
                        }

                        String eventType = switch (chunk.getType()) {
                            case THOUGHT -> "thought";
                            case TOOL_CALL -> "tool_call";
                            case TOOL_RESULT -> "tool_result";
                            case TEXT -> "text";
                            case DONE -> "done";
                            case ERROR -> "error";
                        };
                        String data = switch (chunk.getType()) {
                            case TOOL_CALL -> chunk.getFunctionName() + ":" + chunk.getArguments();
                            case ERROR, TEXT, THOUGHT, TOOL_RESULT -> chunk.getContent();
                            case DONE -> "[DONE]";
                        };
                        return ServerSentEvent.<String>builder()
                                .event(eventType)
                                .data(data)
                                .build();
                    })
                    .doOnComplete(() -> {
                        // 流式输出完成后，保存 Demo 到数据库
                        String fullOutput = outputCollector.toString();
                        if (!fullOutput.isEmpty() && userId != null) {
                            saveDemoSync(userId, req, fullOutput);
                        }
                    });
        }).onErrorResume(e -> Flux.just(errorEvent(e.getMessage())));
    }

    /** 构建 SSE error 事件 */
    private ServerSentEvent<String> errorEvent(String message) {
        return ServerSentEvent.<String>builder()
                .event("error")
                .data(message)
                .build();
    }

    /**
     * 同步保存 Demo（在 doOnComplete 回调中调用）
     * 只存储代码部分和关键词标签，不存储完整解释文本，减少存储压力
     */
    private void saveDemoSync(UUID userId, GenerateDemoRequest req, String output) {
        try {
            String codeContent = extractCodeBlocks(output);
            String title = generateTitle(req.getPrompt());

            Demo demo = new Demo();
            demo.setId(UUID.randomUUID());
            demo.setUserId(userId);
            demo.setTitle(title);
            demo.setPrompt(req.getPrompt());
            demo.setFrameworkId(req.getFrameworkId());
            demo.setCodeContent(codeContent.isEmpty() ? output.substring(0, Math.min(output.length(), 500)) : codeContent);
            demo.setExplanation("");
            demo.setLanguage(req.getLanguage() != null ? req.getLanguage() : "typescript");

            // 用 Jieba TF-IDF 从 prompt + title 提取关键词
            String[] tags = extractKeywords(req.getPrompt(), title);
            demo.setTags(tags);

            demo.setTokensUsed(estimateTokens(output));
            demo.setCreatedAt(Instant.now());
            demoMapper.insert(demo);
            log.info("Demo 已保存: id={}, title={}, tags={}, tokens={}", demo.getId(), demo.getTitle(), String.join(",", tags), demo.getTokensUsed());
        } catch (Exception e) {
            log.error("保存 Demo 失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 从 AI 输出中提取代码块
     */
    private String extractCodeBlocks(String output) {
        StringBuilder code = new StringBuilder();
        int start = 0;
        while (true) {
            int begin = output.indexOf("```", start);
            if (begin == -1) break;
            int end = output.indexOf("```", begin + 3);
            if (end == -1) {
                code.append(output.substring(begin + 3)).append("\n");
                break;
            }
            code.append(output, begin + 3, end).append("\n");
            start = end + 3;
        }
        return code.toString().trim();
    }

    /** 中文停用词（高频无意义词汇） */
    private static final Set<String> STOP_WORDS = Set.of(
            "的", "了", "是", "在", "我", "有", "和", "就", "不", "人", "都", "一", "一个",
            "上", "也", "很", "到", "说", "要", "去", "你", "会", "着", "没有", "看", "好",
            "自己", "这", "他", "她", "它", "们", "那", "里", "为", "什么", "怎么", "如何",
            "帮我", "写", "请", "可以", "能", "给", "用", "做", "把", "被", "让", "从",
            "与", "或", "但", "如果", "因为", "所以", "这个", "那个", "一些", "一下",
            "需要", "想要", "希望", "进行", "实现", "使用", "通过", "然后", "之后", "之前"
    );

    /**
     * 使用 Jieba 分词从 prompt 和 title 中提取关键词
     * 过滤停用词和单字，保留有意义的中英文词汇
     */
    private String[] extractKeywords(String prompt, String title) {
        String text = prompt + " " + (title != null ? title : "");
        try {
            JiebaSegmenter segmenter = new JiebaSegmenter();
            List<SegToken> tokens = segmenter.process(text, JiebaSegmenter.SegMode.SEARCH);
            Set<String> seen = new LinkedHashSet<>();
            for (SegToken token : tokens) {
                String word = token.word.trim();
                // 过滤：空、单字、停用词、纯标点
                if (word.length() < 2 || STOP_WORDS.contains(word) || word.matches("[\\p{P}\\s]+")) {
                    continue;
                }
                seen.add(word);
                if (seen.size() >= 8) break;
            }
            return seen.toArray(String[]::new);
        } catch (Exception e) {
            log.warn("Jieba 分词失败，回退到简单分割: {}", e.getMessage());
            Set<String> fallback = new LinkedHashSet<>();
            for (String word : text.split("[\\s,，。、;；]+")) {
                if (word.length() >= 2 && word.length() <= 20) {
                    fallback.add(word);
                }
            }
            return fallback.stream().limit(8).toArray(String[]::new);
        }
    }

    // ==================== 保存和查询 ====================

    public Mono<Demo> saveDemo(UUID userId, GenerateDemoRequest req,
                               String codeContent, String explanation) {
        return Mono.fromCallable(() -> {
            Demo demo = new Demo();
            demo.setId(UUID.randomUUID());
            demo.setUserId(userId);
            demo.setTitle(generateTitle(req.getPrompt()));
            demo.setPrompt(req.getPrompt());
            demo.setFrameworkId(req.getFrameworkId());
            demo.setCodeContent(codeContent);
            demo.setExplanation(explanation);
            demo.setLanguage(req.getLanguage() != null ? req.getLanguage() : "typescript");
            demo.setTags(new String[0]);
            demo.setCreatedAt(Instant.now());
            demoMapper.insert(demo);
            return demo;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<com.baomidou.mybatisplus.extension.plugins.pagination.Page<Demo>> getUserDemos(
            UUID userId, int page, int size, String keyword) {
        return Mono.fromCallable(() -> {
            com.baomidou.mybatisplus.extension.plugins.pagination.Page<Demo> pageParam =
                    new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size);
            LambdaQueryWrapper<Demo> wrapper = new LambdaQueryWrapper<Demo>()
                    .eq(Demo::getUserId, userId)
                    .orderByDesc(Demo::getCreatedAt);
            if (keyword != null && !keyword.isBlank()) {
                wrapper.and(w -> w
                        .like(Demo::getTitle, keyword)
                        .or()
                        .like(Demo::getPrompt, keyword)
                        .or()
                        .like(Demo::getLanguage, keyword)
                        .or()
                        .like(Demo::getTags, keyword));
            }
            return demoMapper.selectPage(pageParam, wrapper);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Demo> getDemo(UUID id, UUID userId) {
        return Mono.fromCallable(() -> {
            Demo demo = demoMapper.selectById(id);
            if (demo != null && !demo.getUserId().equals(userId)) {
                return null;  // 不属于当前用户，返回 403
            }
            return demo;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Void> deleteDemo(UUID id, UUID userId) {
        return Mono.fromCallable(() -> {
            Demo demo = demoMapper.selectById(id);
            if (demo == null) throw new RuntimeException("Demo 不存在");
            if (!demo.getUserId().equals(userId)) throw new RuntimeException("无权删除此 Demo");
            demoMapper.deleteById(id);
            return null;
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }

    // ==================== 内部方法 ====================

    /**
     * 构建系统提示词
     * 根据用户选择的语言和框架定制 AI 行为
     */
    private String buildSystemPrompt(GenerateDemoRequest req) {
        String lang = req.getLanguage() != null ? req.getLanguage() : "TypeScript";

        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个专业的编程助手。用户会描述一个编程需求，你需要：\n");
        prompt.append("1. 生成清晰、可运行的代码示例\n");
        prompt.append("2. 用中文解释代码的关键部分\n\n");
        prompt.append("编程语言: ").append(lang).append("\n");

        if (req.getFrameworkId() != null) {
            Framework fw = frameworkMapper.selectById(req.getFrameworkId());
            if (fw != null) {
                prompt.append("框架: ").append(fw.getName())
                        .append(" (").append(fw.getBaseUrl()).append(")\n");
            }
        }

        prompt.append("\n重要规则：\n");
        prompt.append("- 如果用户的问题涉及特定框架，先用 search_links 搜索官方文档\n");
        prompt.append("- 如果不确定框架的用法，先用 get_framework_info 获取框架信息\n");
        prompt.append("- 基于搜索到的文档和最佳实践生成代码\n");
        prompt.append("- 代码要完整可运行，包含必要的 import\n");
        prompt.append("- 每个语句独占一行，不要把多个语句写在同一行\n");
        prompt.append("- 代码用 ```language 包裹，language 替换为实际语言\n");
        prompt.append("- 解释要简洁，不要重复代码内容\n");

        prompt.append("\n工具使用规则：\n");
        prompt.append("- 你有以下工具可用：search_links（搜索框架文档链接）、get_framework_info（获取框架信息）\n");
        prompt.append("- 优先用工具获取准确信息，不要凭记忆编造文档链接\n");
        prompt.append("- 拿到足够信息后立即给出完整回答，不要重复调用相同工具\n");
        prompt.append("- 如果工具返回空结果，直接基于已有知识回答\n");

        return prompt.toString();
    }

    /**
     * 构建工具处理器映射
     * 注册每个工具的实际执行逻辑
     */
    private Map<String, ToolHandler> buildToolHandlers() {
        Map<String, ToolHandler> handlers = new HashMap<>();

        // search_links: 搜索知识链接
        handlers.put("search_links", args -> {
            try {
                String query = extractJsonString(args, "query");
                log.info("工具 search_links 执行，query={}", query);
                var results = knowledgeService.searchLinks(query).block();
                log.info("工具 search_links 结果数: {}", results != null ? results.size() : 0);
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
        });

        // get_framework_info: 获取框架信息
        handlers.put("get_framework_info", args -> {
            try {
                String slug = extractJsonString(args, "slug");
                Framework fw = frameworkMapper.selectOne(
                        new LambdaQueryWrapper<Framework>().eq(Framework::getSlug, slug));
                if (fw == null) return "未找到框架: " + slug;
                return String.format("框架: %s\n文档: %s\n分类: %s\n简介: %s",
                        fw.getName(), fw.getBaseUrl(), fw.getCategory(), fw.getDescription());
            } catch (Exception e) {
                return "获取框架信息失败: " + e.getMessage();
            }
        });

        return handlers;
    }

    /**
     * 从 JSON 字符串中提取指定字段值
     */
    private String extractJsonString(String json, String field) {
        try {
            com.fasterxml.jackson.databind.JsonNode node =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
            return node.has(field) ? node.get(field).asText() : "";
        } catch (Exception e) {
            return "";
        }
    }

    private String generateTitle(String prompt) {
        return prompt.length() <= 30 ? prompt : prompt.substring(0, 30) + "...";
    }

    /**
     * 粗略估算 token 数（中文约 1.5 token/字符，英文约 0.75 token/word）
     */
    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        return (int) (text.length() * 1.5);
    }

    /**
     * 获取用户近 7 天的 Token 消耗统计
     * 按天聚合 tokens_used
     */
    public List<AiConfigResponse.TokenUsage> getTokenUsage(UUID userId) {
        // 查询近 7 天的 demo 记录
        Instant sevenDaysAgo = Instant.now().minus(Duration.ofDays(7));
        List<Demo> demos = demoMapper.selectList(
                new LambdaQueryWrapper<Demo>()
                        .eq(Demo::getUserId, userId)
                        .ge(Demo::getCreatedAt, sevenDaysAgo)
                        .orderByAsc(Demo::getCreatedAt));

        // 按日期聚合
        Map<String, Long> dailyUsage = new LinkedHashMap<>();
        for (int i = 6; i >= 0; i--) {
            String date = Instant.now().minus(Duration.ofDays(i))
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate().toString();
            dailyUsage.put(date, 0L);
        }

        for (Demo demo : demos) {
            String date = demo.getCreatedAt()
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate().toString();
            dailyUsage.merge(date, demo.getTokensUsed() != null ? demo.getTokensUsed().longValue() : 0L, Long::sum);
        }

        return dailyUsage.entrySet().stream()
                .map(e -> new AiConfigResponse.TokenUsage(e.getKey(), e.getValue()))
                .toList();
    }
}
