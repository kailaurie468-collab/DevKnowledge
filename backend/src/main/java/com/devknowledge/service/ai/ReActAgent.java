package com.devknowledge.service.ai;

import com.devknowledge.model.UserAiConfig;
import com.devknowledge.service.SensitiveDataSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ReAct (Reasoning + Acting) Agent 引擎
 * 全响应式实现，不使用 block()，兼容 Netty 事件循环线程
 */
@Component
public class ReActAgent {

    private static final Logger log = LoggerFactory.getLogger(ReActAgent.class);
    private static final int DEFAULT_MAX_ITERATIONS = 5;
    private static final int ABSOLUTE_MAX_ITERATIONS = 8;

    private final AiProviderFactory aiProviderFactory;

    public ReActAgent(AiProviderFactory aiProviderFactory) {
        this.aiProviderFactory = aiProviderFactory;
    }

    /**
     * 运行 ReAct 循环（使用默认最大轮数）
     */
    public Flux<AiChunk> run(String systemPrompt, String userMessage,
                              List<AiFunction> tools, Map<String, ToolHandler> handlers,
                              UserAiConfig config) {
        return run(systemPrompt, userMessage, tools, handlers, config, DEFAULT_MAX_ITERATIONS);
    }

    /**
     * 运行 ReAct 循环
     *
     * @param maxIterations 最大推理轮数（1-8，超出范围自动修正）
     */
    public Flux<AiChunk> run(String systemPrompt, String userMessage,
                              List<AiFunction> tools, Map<String, ToolHandler> handlers,
                              UserAiConfig config, int maxIterations) {
        return run(systemPrompt, userMessage, tools, handlers, config, maxIterations, null);
    }

    /**
     * 运行 ReAct 循环（支持工具调用次数追踪）
     *
     * @param maxIterations  最大推理轮数（1-8，超出范围自动修正）
     * @param toolCallCounts 工具调用次数统计 map，传 null 则不追踪
     */
    public Flux<AiChunk> run(String systemPrompt, String userMessage,
                              List<AiFunction> tools, Map<String, ToolHandler> handlers,
                              UserAiConfig config, int maxIterations,
                              Map<String, AtomicInteger> toolCallCounts) {

        int effectiveMax = Math.max(1, Math.min(maxIterations, ABSOLUTE_MAX_ITERATIONS));
        log.info("ReAct Agent 启动，maxIterations={}", effectiveMax);

        Sinks.Many<AiChunk> sink = Sinks.many().unicast().onBackpressureBuffer();
        AiProviderAdapter adapter = aiProviderFactory.getAdapter(config.getProvider());
        List<AiProviderAdapter.ChatMessage> messages = Collections.synchronizedList(new ArrayList<>());
        messages.add(new AiProviderAdapter.ChatMessage("user", userMessage));

        AtomicInteger iteration = new AtomicInteger(0);
        // 死循环检测：记录上一轮的 (fnName, args) 签名
        List<String> lastRoundSignatures = Collections.synchronizedList(new ArrayList<>());
        // 连续全失败轮次计数（连续 2 轮全部工具失败才硬停止）
        AtomicInteger consecutiveAllFail = new AtomicInteger(0);

        runRound(adapter, systemPrompt, messages, tools, handlers, config, sink, iteration, effectiveMax, lastRoundSignatures, consecutiveAllFail, toolCallCounts);

        return sink.asFlux();
    }

    /**
     * 递归执行一轮 AI 调用
     */
    private void runRound(AiProviderAdapter adapter, String systemPrompt,
                           List<AiProviderAdapter.ChatMessage> messages,
                           List<AiFunction> tools, Map<String, ToolHandler> handlers,
                           UserAiConfig config, Sinks.Many<AiChunk> sink,
                           AtomicInteger iteration, int maxIterations,
                           List<String> lastRoundSignatures,
                           AtomicInteger consecutiveAllFail,
                           Map<String, AtomicInteger> toolCallCounts) {

        int currentRound = iteration.getAndIncrement();
        log.info("ReAct 第 {} 轮开始，消息数: {}", currentRound + 1, messages.size());

        AtomicBoolean hasToolCall = new AtomicBoolean(false);
        AtomicInteger textOutputLength = new AtomicInteger(0);
        List<AiChunk> toolCallChunks = Collections.synchronizedList(new ArrayList<>());
        // 收集本轮文本输出，结束后加到 messages，让模型知道自己说了什么
        StringBuilder roundTextCollector = new StringBuilder();

        adapter.streamWithTools(systemPrompt, messages, tools, config)
                .doOnNext(chunk -> {
                    if (chunk.getType() == AiChunkType.TOOL_CALL) {
                        hasToolCall.set(true);
                        toolCallChunks.add(chunk);
                        sink.tryEmitNext(AiChunk.toolCall(chunk.getFunctionName(), chunk.getArguments()));
                    } else if (chunk.getType() == AiChunkType.TEXT) {
                        // 追踪文本输出总长度
                        if (chunk.getContent() != null) {
                            textOutputLength.addAndGet(chunk.getContent().length());
                            roundTextCollector.append(chunk.getContent());
                        }
                        sink.tryEmitNext(chunk);
                    }
                })
                .doOnError(e -> {
                    log.error("AI 调用异常: type={}, message={}",
                            e.getClass().getSimpleName(), SensitiveDataSanitizer.sanitize(e.getMessage()));
                    sink.tryEmitNext(AiChunk.error("AI 调用异常: " + e.getMessage()));
                    sink.tryEmitNext(AiChunk.done());
                    sink.tryEmitComplete();
                })
                .doOnComplete(() -> {
                    log.info("ReAct 第 {} 轮完成，hasToolCall={}，textLength={}", currentRound + 1, hasToolCall.get(), textOutputLength.get());

                    // 模型没有调用工具 → 认为已完成回答，正常结束
                    // ReAct 模式的核心设计——没有工具调用就意味着模型认为已有足够信息直接回答。
                    if (!hasToolCall.get()) {
                        log.info("本轮无工具调用，结束推理");
                        sink.tryEmitNext(AiChunk.done());
                        sink.tryEmitComplete();
                        return;
                    }

                    // 达到最大轮数 → 强制结束
                    if (currentRound + 1 >= maxIterations) {
                        sink.tryEmitNext(AiChunk.text("\n\n[已达到最大推理轮数 " + maxIterations + "]"));
                        sink.tryEmitNext(AiChunk.done());
                        sink.tryEmitComplete();
                        return;
                    }

                    // 死循环检测：本轮所有工具调用的签名（参数排序后归一化，避免 "a b" vs "b a" 被认为不同）
                    List<String> currentSignatures = new ArrayList<>();
                    boolean loopDetected = false;

                    for (AiChunk tc : toolCallChunks) {
                        String normalizedArgs = normalizeArgs(tc.getArguments());
                        String sig = tc.getFunctionName() + ":" + normalizedArgs;
                        currentSignatures.add(sig);
                        if (lastRoundSignatures.contains(sig)) {
                            loopDetected = true;
                            log.warn("检测到死循环：工具 {} 参数相同", tc.getFunctionName());
                        }
                    }
                    if (loopDetected) {
                        sink.tryEmitNext(AiChunk.text("\n\n[检测到重复工具调用，已停止推理]"));
                        sink.tryEmitNext(AiChunk.done());
                        sink.tryEmitComplete();
                        return;
                    }

                    // 把本轮文本输出加到 messages，让模型下一轮知道自己说了什么
                    String roundText = roundTextCollector.toString().trim();
                    if (!roundText.isEmpty()) {
                        messages.add(new AiProviderAdapter.ChatMessage("assistant", roundText));
                    }

                    // 执行工具并检查结果
                    boolean allFailed = true;

                    // 没有工具调用时不算失败（模型直接生成了文本）
                    if (toolCallChunks.isEmpty()) {
                        allFailed = false;
                    }

                    for (AiChunk tc : toolCallChunks) {
                        ToolResult result = executeTool(tc, handlers, messages, sink, toolCallCounts);
                        if (!result.isEmpty() && !result.isError()) {
                            allFailed = false;
                        }
                    }

                    if (allFailed) {
                        int failCount = consecutiveAllFail.incrementAndGet();
                        log.info("本轮所有工具未返回有效结果，连续失败轮数: {}", failCount);

                        // 连续 2 轮全部失败 → 硬停止，防止浪费 token
                        if (failCount >= 2) {
                            sink.tryEmitNext(AiChunk.text("\n\n[连续多轮工具调用失败，已停止推理]"));
                            sink.tryEmitNext(AiChunk.done());
                            sink.tryEmitComplete();
                            return;
                        }

                        // 首次全失败 → 给模型一次机会，让它基于已有信息回答
                    } else {
                        consecutiveAllFail.set(0);
                    }

                    // 更新死循环签名
                    lastRoundSignatures.clear();
                    lastRoundSignatures.addAll(currentSignatures);

                    // 继续下一轮
                    runRound(adapter, systemPrompt, messages, tools, handlers, config, sink,
                            iteration, maxIterations, lastRoundSignatures, consecutiveAllFail, toolCallCounts);
                })
                .subscribe();
    }

    /**
     * 工具执行结果
     */
    private record ToolResult(boolean isEmpty, boolean isError) {}

    /**
     * 归一化工具参数：提取 JSON 中的值，排序后拼接
     * 用于死循环检测，避免 "android viewmodel" vs "viewmodel android" 被认为不同
     */
    private String normalizeArgs(String args) {
        if (args == null || args.isBlank()) return "";
        try {
            // 简单提取所有值并排序
            java.util.List<String> values = new java.util.ArrayList<>();
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"([^\"]+)\"").matcher(args);
            while (m.find()) {
                values.add(m.group(1).toLowerCase().trim());
            }
            java.util.Collections.sort(values);
            return String.join(" ", values);
        } catch (Exception e) {
            return args.toLowerCase().trim();
        }
    }

    /**
     * 执行工具调用，将结果注入对话历史
     */
    private ToolResult executeTool(AiChunk toolCall, Map<String, ToolHandler> handlers,
                                    List<AiProviderAdapter.ChatMessage> messages,
                                    Sinks.Many<AiChunk> sink,
                                    Map<String, AtomicInteger> toolCallCounts) {
        String fnName = toolCall.getFunctionName();
        String fnArgs = toolCall.getArguments();
        String reasoningContent = toolCall.getReasoningContent();
        log.info("执行工具: {}, argsLength={}", fnName, fnArgs != null ? fnArgs.length() : 0);

        // fnName 为 null 说明 AI 返回了畸形的 tool_call，直接抛异常终止推理
        if (fnName == null || fnName.isBlank()) {
            throw new IllegalStateException("AI 返回了无效的工具调用（function name 为 null），终止推理");
        }

        ToolHandler handler = handlers.get(fnName);
        if (handler == null) {
            log.error("未知工具: {}", fnName);
            sink.tryEmitNext(AiChunk.error("未知工具: " + fnName));
            messages.add(new AiProviderAdapter.ChatMessage("assistant", "错误: 未知工具 " + fnName));
            return new ToolResult(true, true);
        }

        try {
            String result = handler.apply(fnArgs);
            log.info("工具 {} 执行完成，结果长度: {}", fnName, result != null ? result.length() : 0);

            // 记录工具调用次数
            if (toolCallCounts != null) {
                toolCallCounts.computeIfAbsent(fnName, k -> new AtomicInteger(0)).incrementAndGet();
            }
            sink.tryEmitNext(AiChunk.text("\n[工具 " + fnName + " 执行完成]\n"));

            boolean isEmpty = result == null || result.isBlank();

            // 保留 reasoning_content 到下一轮，模型建议多轮对话中传递以获得最佳表现
            String assistantContent = "我调用了工具 " + fnName + " 来搜索信息。";
            messages.add(new AiProviderAdapter.ChatMessage("assistant", assistantContent, null, reasoningContent));
            messages.add(new AiProviderAdapter.ChatMessage("user",
                    "工具 " + fnName + " 的返回结果:\n" + result + "\n请基于以上信息继续回答。"));

            return new ToolResult(isEmpty, false);
        } catch (Exception e) {
            log.error("工具 {} 执行失败: type={}, message={}",
                    fnName, e.getClass().getSimpleName(), SensitiveDataSanitizer.sanitize(e.getMessage()));
            String error = "工具 " + fnName + " 执行失败: " + e.getMessage();
            sink.tryEmitNext(AiChunk.error(error));
            messages.add(new AiProviderAdapter.ChatMessage("user", "错误: " + error));
            return new ToolResult(true, true);
        }
    }
}
