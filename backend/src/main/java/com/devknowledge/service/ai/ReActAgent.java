package com.devknowledge.service.ai;

import com.devknowledge.model.UserAiConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * ReAct (Reasoning + Acting) Agent 引擎
 * 全响应式实现，不使用 block()，兼容 Netty 事件循环线程
 */
@Component
public class ReActAgent {

    private static final Logger log = LoggerFactory.getLogger(ReActAgent.class);
    private static final int DEFAULT_MAX_ITERATIONS = 5;
    private static final int ABSOLUTE_MAX_ITERATIONS = 8;
    /** 完成信号关键词正则 */
    private static final Pattern COMPLETION_PATTERN = Pattern.compile(
            "以下是最终答案|最终回答|总结如下|以下是完整的|以上就是|综上所述|代码如下");
    /** 完成信号最小文本长度 */
    private static final int MIN_COMPLETION_TEXT_LENGTH = 100;

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
                        }
                        sink.tryEmitNext(chunk);
                    }
                })
                .doOnError(e -> {
                    log.error("AI 调用异常: {}", e.getMessage(), e);
                    sink.tryEmitNext(AiChunk.error("AI 调用异常: " + e.getMessage()));
                    sink.tryEmitNext(AiChunk.done());
                    sink.tryEmitComplete();
                })
                .doOnComplete(() -> {
                    log.info("ReAct 第 {} 轮完成，hasToolCall={}，textLength={}", currentRound + 1, hasToolCall.get(), textOutputLength.get());

                    // 完成信号检测：后半程 + 无工具调用 + 足够文本 + 包含完成关键词
                    if (!hasToolCall.get()
                            && currentRound + 1 >= maxIterations / 2
                            && textOutputLength.get() >= MIN_COMPLETION_TEXT_LENGTH) {
                        // 检查本轮文本是否包含完成信号
                        String roundText = messages.stream()
                                .filter(m -> "assistant".equals(m.role()))
                                .map(AiProviderAdapter.ChatMessage::content)
                                .reduce("", (a, b) -> a + " " + b);
                        if (COMPLETION_PATTERN.matcher(roundText).find()) {
                            log.info("检测到完成信号关键词，结束推理");
                            sink.tryEmitNext(AiChunk.done());
                            sink.tryEmitComplete();
                            return;
                        }
                    }

                    // 模型没有调用工具 → 正常结束
                    if (!hasToolCall.get()) {
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

                    // 死循环检测：本轮所有工具调用的签名
                    List<String> currentSignatures = new ArrayList<>();
                    boolean loopDetected = false;

                    for (AiChunk tc : toolCallChunks) {
                        String sig = tc.getFunctionName() + ":" + tc.getArguments();
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

                    // 执行工具并检查结果
                    boolean allFailed = true;

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
     * 执行工具调用，将结果注入对话历史
     */
    private ToolResult executeTool(AiChunk toolCall, Map<String, ToolHandler> handlers,
                                    List<AiProviderAdapter.ChatMessage> messages,
                                    Sinks.Many<AiChunk> sink,
                                    Map<String, AtomicInteger> toolCallCounts) {
        String fnName = toolCall.getFunctionName();
        String fnArgs = toolCall.getArguments();
        log.info("执行工具: {}({})", fnName, fnArgs);

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

            messages.add(new AiProviderAdapter.ChatMessage("assistant",
                    "我调用了工具 " + fnName + " 来搜索信息。"));
            messages.add(new AiProviderAdapter.ChatMessage("user",
                    "工具 " + fnName + " 的返回结果:\n" + result + "\n请基于以上信息继续回答。"));

            return new ToolResult(isEmpty, false);
        } catch (Exception e) {
            log.error("工具 {} 执行失败: {}", fnName, e.getMessage(), e);
            String error = "工具 " + fnName + " 执行失败: " + e.getMessage();
            sink.tryEmitNext(AiChunk.error(error));
            messages.add(new AiProviderAdapter.ChatMessage("user", "错误: " + error));
            return new ToolResult(true, true);
        }
    }
}
