package com.devknowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.huaban.analysis.jieba.JiebaSegmenter;
import com.huaban.analysis.jieba.SegToken;
import com.devknowledge.dto.AiConfigResponse;
import com.devknowledge.dto.GenerateDemoRequest;
import com.devknowledge.dto.KbChunkSearchResult;
import com.devknowledge.dto.RagMetricResponse;
import com.devknowledge.mapper.DemoMapper;
import com.devknowledge.mapper.FrameworkMapper;
import com.devknowledge.mapper.RagMetricMapper;
import com.devknowledge.mapper.UserAiConfigMapper;
import com.devknowledge.model.Demo;
import com.devknowledge.model.Framework;
import com.devknowledge.model.RagMetric;
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
import java.util.concurrent.atomic.AtomicInteger;

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
    private final DemoToolProvider toolProvider;
    private final KbService kbService;
    private final RagMetricMapper ragMetricMapper;

    @Value("${jwt.secret}")
    private String aesSecret;

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
        return Flux.deferContextual(contextView -> generateDemoWithTiming(
                userId,
                req,
                contextView.getOrDefault(RequestTiming.CONTEXT_KEY, null)));
    }

    private Flux<ServerSentEvent<String>> generateDemoWithTiming(
            UUID userId,
            GenerateDemoRequest req,
            RequestTiming timing) {
        if (userId == null) {
            return Flux.just(ServerSentEvent.<String>builder()
                    .data("[ERROR]请先登录并配置 AI 服务商").build());
        }

        return Mono.defer(() -> {
            RequestTiming.Stage configStage = timing == null ? null : timing.startStage("ai_config");
            return Mono.fromCallable(() -> {
                UserAiConfig config = aiConfigService.getActiveConfigEntity(userId);
                if (config == null) {
                    throw new RuntimeException("请先在设置页配置 AI 服务商");
                }
                AesUtil aes = new AesUtil(aesSecret);
                config.setApiKey(aes.decrypt(config.getApiKey()));
                return config;
            }).doFinally(signal -> finishStage(configStage, signal.name()));
        }).subscribeOn(Schedulers.boundedElastic())
        .flatMapMany(config -> {
            String systemPromptBase = buildSystemPrompt(req);
            List<AiFunction> tools = new ArrayList<>(toolProvider.getBaseTools());
            Map<String, ToolHandler> handlers = new HashMap<>(toolProvider.getBaseHandlers());

            Map<String, AtomicInteger> toolCallCounts = new HashMap<>();

            if (req.getKbId() != null) {
                boolean hasEmbedding = kbService.hasEmbeddingConfig(userId);
                String ragWarning = null;
                if (!hasEmbedding) {
                    ragWarning = "未配置 Embedding AI，当前使用关键词搜索，检索效果可能较差。建议在设置页配置 Embedding 以获得更好的语义检索效果。";
                    log.warn("用户 {} 使用 RAG 模式但未配置 Embedding AI", userId);
                }

                int topK = req.getTopK() != null ? req.getTopK() : 5;
                RagMetric ragMetric = new RagMetric();
                ragMetric.setId(UUID.randomUUID());
                ragMetric.setUserId(userId);
                ragMetric.setKbId(req.getKbId());
                ragMetric.setTopK(topK);
                ragMetric.setToolCallCount(0);
                ragMetric.setCreatedAt(Instant.now());

                tools.add(toolProvider.getKbTool());
                handlers.put("search_kb", toolProvider.getKbHandler(userId, req.getKbId()));

                long startTime = System.nanoTime();
                final String finalRagWarning = ragWarning;

                UUID demoId = UUID.randomUUID();

                return kbService.searchKbVector(userId, req.getKbId(), req.getPrompt(), topK)
                        .map(contextChunks -> {
                            long retrievalMs = elapsedMillis(startTime);
                            log.info("RAG预检索文档数量: {}", contextChunks.size());

                            String promptWithContext = systemPromptBase;
                            if (contextChunks != null && !contextChunks.isEmpty()) {
                                promptWithContext += buildRagContext(contextChunks);

                                double avgSim = contextChunks.stream().mapToDouble(KbChunkSearchResult::getScore).average().orElse(0);
                                double maxSim = contextChunks.stream().mapToDouble(KbChunkSearchResult::getScore).max().orElse(0);
                                double minSim = contextChunks.stream().mapToDouble(KbChunkSearchResult::getScore).min().orElse(0);

                                ragMetric.setRagUsed(true);
                                ragMetric.setChunkCount(contextChunks.size());
                                ragMetric.setAvgSimilarity(avgSim);
                                ragMetric.setMaxSimilarity(maxSim);
                                ragMetric.setMinSimilarity(minSim);
                                ragMetric.setRetrievalMs((int) retrievalMs);
                            } else {
                                ragMetric.setRagUsed(false);
                                ragMetric.setRetrievalMs((int) retrievalMs);
                            }
                            ragMetric.setDemoId(demoId);
                            return new RagContextResult(promptWithContext, ragMetric, finalRagWarning, demoId);
                        })
                        .onErrorResume(e -> {
                            log.error("RAG 预检索失败，继续无 RAG 生成: {}",
                                    SensitiveDataSanitizer.sanitize(e.getMessage()));
                            ragMetric.setRagUsed(false);
                            ragMetric.setDemoId(demoId);
                            return Mono.just(new RagContextResult(systemPromptBase, ragMetric, finalRagWarning, demoId));
                        })
                        .flatMapMany(ragRes -> runAgent(userId, req, config, ragRes.prompt, tools,
                                handlers, toolCallCounts, ragRes.metric, ragRes.warning, ragRes.demoId, timing));
            } else {
                UUID demoId = UUID.randomUUID();
                return runAgent(userId, req, config, systemPromptBase, tools, handlers,
                        toolCallCounts, null, null, demoId, timing);
            }
        }).onErrorResume(e -> {
            if (timing != null) {
                timing.markError(e);
                timing.markFirstEvent();
            }
            String msg = SensitiveDataSanitizer.sanitize(e.getMessage());
            if (msg.equals("未知错误")) {
                msg = "生成失败";
            }
            // 确保错误消息前缀，前端可以正确识别
            if (!msg.startsWith("[ERROR]")) msg = "[ERROR]" + msg;
            return Flux.just(errorEvent(msg));
        });
    }

    private record RagContextResult(String prompt, RagMetric metric, String warning, UUID demoId) {}

    private Flux<ServerSentEvent<String>> runAgent(UUID userId, GenerateDemoRequest req, UserAiConfig config,
                                                   String systemPrompt, List<AiFunction> tools, Map<String, ToolHandler> handlers,
                                                   Map<String, AtomicInteger> toolCallCounts, RagMetric finalRagMetric,
                                                   String finalRagWarning, UUID demoId, RequestTiming timing) {
        StringBuilder outputCollector = new StringBuilder();
        int maxIter = req.getMaxIterations() != null ? req.getMaxIterations() : 5;

        Flux<AiChunk> aiFlux = Flux.defer(() -> {
            RequestTiming.Stage llmStage = timing == null ? null : timing.startStage("llm_generation");
            return reactAgent.run(systemPrompt, req.getPrompt(), tools, handlers, config, maxIter, toolCallCounts)
                    .doFinally(signal -> finishStage(llmStage, signal.name()));
        });

        Flux<ServerSentEvent<String>> agentFlux = aiFlux
                .map(chunk -> {
                    if (chunk.getType() == AiChunkType.TEXT && chunk.getContent() != null) {
                        outputCollector.append(chunk.getContent());
                    }
                    if (chunk.getType() == AiChunkType.ERROR && timing != null) {
                        timing.markLogicalError("AI_ERROR", chunk.getContent());
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
                .doOnCancel(() -> {
                    String fullOutput = outputCollector.toString();
                    if (!fullOutput.isEmpty() && userId != null) {
                        saveDemoSync(userId, req, fullOutput, demoId);
                        log.info("Demo 已保存（用户取消）");
                    }
                })
                .doOnComplete(() -> {
                    String fullOutput = outputCollector.toString();
                    if (!fullOutput.isEmpty() && userId != null) {
                        saveDemoSync(userId, req, fullOutput, demoId);
                        log.info("Demo 已保存（正常完成）");
                    }

                    if (finalRagMetric != null && userId != null) {
                        try {
                            AtomicInteger kbCount = toolCallCounts.get("search_kb");
                            finalRagMetric.setToolCallCount(kbCount != null ? kbCount.get() : 0);
                            ragMetricMapper.insert(finalRagMetric);
                            log.info("RAG 指标已保存: avgSim={}, retrievalMs={}",
                                    finalRagMetric.getAvgSimilarity(), finalRagMetric.getRetrievalMs());
                        } catch (Exception e) {
                            log.warn("保存 RAG 指标失败: {}", e.getMessage());
                        }
                    }
                });

        if (finalRagWarning != null) {
            ServerSentEvent<String> warnEvent = ServerSentEvent.<String>builder()
                    .event("warning")
                    .data(finalRagWarning)
                    .build();
            agentFlux = Flux.just(warnEvent).concatWith(agentFlux);
        }
        return agentFlux.doOnNext(event -> {
            if (timing == null) return;
            timing.markFirstEvent();
            if ("text".equals(event.event())) {
                timing.markFirstText();
            }
        });
    }

    /** 构建 SSE error 事件 */
    private ServerSentEvent<String> errorEvent(String message) {
        return ServerSentEvent.<String>builder()
                .event("error")
                .data(message)
                .build();
    }

    private static long elapsedMillis(long startNanos) {
        return Math.max(0, (System.nanoTime() - startNanos) / 1_000_000);
    }

    /**
     * 将 Reactor 终止信号映射为阶段状态，确保异常和取消也能被观测。
     */
    private static void finishStage(RequestTiming.Stage stage, String signalName) {
        if (stage == null) return;
        stage.finish("CANCEL".equals(signalName)
                ? "CANCELLED"
                : "ON_ERROR".equals(signalName) ? "ERROR" : "SUCCESS");
    }

    /**
     * 同步保存 Demo（在 doOnComplete/doOnCancel 回调中调用）
     * 代码块和解释分开存储，便于历史记录展示
     */
    private void saveDemoSync(UUID userId, GenerateDemoRequest req, String output, UUID demoId) {
        try {
            String title = generateTitle(req.getPrompt());

            Demo demo = new Demo();
            demo.setId(demoId);
            demo.setUserId(userId);
            demo.setTitle(title);
            demo.setPrompt(req.getPrompt());
            demo.setFrameworkId(req.getFrameworkId());
            demo.setCodeContent("");
            // 完整输出作为解释保存，保留原有的 Markdown 格式（多段代码和文本交织）
            demo.setExplanation(output);
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

    // 已移除 extractCodeBlocks 和 extractExplanation

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
        prompt.append("- 直接基于你的知识生成代码，不要调用任何工具\n");
        prompt.append("- 代码要完整可运行，包含必要的 import\n");
        prompt.append("- 每个语句独占一行，不要把多个语句写在同一行\n");
        prompt.append("- 代码用 ```language 包裹，language 替换为实际语言\n");
        prompt.append("- 解释要简洁，不要重复代码内容\n");

        return prompt.toString();
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

    private String buildRagContext(List<KbChunkSearchResult> chunks) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n以下是知识库中的相关参考内容（已自动检索）：\n");
        sb.append("请优先参考这些内容回答问题，如果信息不足可以调用 search_kb 工具进一步搜索。\n\n");
        for (int i = 0; i < chunks.size(); i++) {
            KbChunkSearchResult chunk = chunks.get(i);
            sb.append(String.format("[%d] 来源: %s (相关度: %.0f%%)\n",
                    i + 1,
                    chunk.getFilename() != null ? chunk.getFilename() : "未知",
                    chunk.getScore() * 100));
            sb.append(chunk.getContent()).append("\n\n");
        }
        return sb.toString();
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

    /**
     * 获取用户近 7 天的 RAG 指标
     */
    public List<RagMetricResponse> getRagMetrics(UUID userId) {
        Instant sevenDaysAgo = Instant.now().minus(Duration.ofDays(7));
        List<RagMetric> metrics = ragMetricMapper.selectList(
                new LambdaQueryWrapper<RagMetric>()
                        .eq(RagMetric::getUserId, userId)
                        .ge(RagMetric::getCreatedAt, sevenDaysAgo)
                        .orderByDesc(RagMetric::getCreatedAt));

        List<RagMetricResponse> responses = new ArrayList<>();
        for (RagMetric m : metrics) {
            RagMetricResponse r = new RagMetricResponse();
            r.setDemoId(m.getDemoId());
            r.setKbId(m.getKbId());
            r.setRagUsed(m.getRagUsed());
            r.setTopK(m.getTopK());
            r.setChunkCount(m.getChunkCount());
            r.setAvgSimilarity(m.getAvgSimilarity());
            r.setMaxSimilarity(m.getMaxSimilarity());
            r.setMinSimilarity(m.getMinSimilarity());
            r.setRetrievalMs(m.getRetrievalMs());
            r.setToolCallCount(m.getToolCallCount());
            r.setCreatedAt(m.getCreatedAt());

            // 查询 Demo 标题
            Demo demo = demoMapper.selectById(m.getDemoId());
            r.setDemoTitle(demo != null ? demo.getTitle() : "未知");

            responses.add(r);
        }
        return responses;
    }
}
