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

/**
 * ReAct (Reasoning + Acting) Agent 引擎
 * 全响应式实现，不使用 block()，兼容 Netty 事件循环线程
 */
@Component
public class ReActAgent {

    private static final Logger log = LoggerFactory.getLogger(ReActAgent.class);
    private static final int MAX_ITERATIONS = 3;

    private final AiProviderFactory aiProviderFactory;

    public ReActAgent(AiProviderFactory aiProviderFactory) {
        this.aiProviderFactory = aiProviderFactory;
    }

    /**
     * 运行 ReAct 循环
     * 每个 chunk 实时推送给前端
     */
    public Flux<AiChunk> run(String systemPrompt, String userMessage,
                              List<AiFunction> tools, Map<String, ToolHandler> handlers,
                              UserAiConfig config) {
        Sinks.Many<AiChunk> sink = Sinks.many().unicast().onBackpressureBuffer();
        AiProviderAdapter adapter = aiProviderFactory.getAdapter(config.getProvider());
        List<AiProviderAdapter.ChatMessage> messages = Collections.synchronizedList(new ArrayList<>());
        messages.add(new AiProviderAdapter.ChatMessage("user", userMessage));

        AtomicInteger iteration = new AtomicInteger(0);

        // 启动第一轮
        runRound(adapter, systemPrompt, messages, tools, handlers, config, sink, iteration);

        return sink.asFlux();
    }

    /**
     * 递归执行一轮 AI 调用
     * 每轮完成后如果有工具调用，自动触发下一轮
     */
    private void runRound(AiProviderAdapter adapter, String systemPrompt,
                           List<AiProviderAdapter.ChatMessage> messages,
                           List<AiFunction> tools, Map<String, ToolHandler> handlers,
                           UserAiConfig config, Sinks.Many<AiChunk> sink,
                           AtomicInteger iteration) {

        int currentRound = iteration.getAndIncrement();
        log.info("ReAct 第 {} 轮开始，消息数: {}", currentRound + 1, messages.size());

        AtomicBoolean hasToolCall = new AtomicBoolean(false);
        List<AiChunk> toolCallChunks = Collections.synchronizedList(new ArrayList<>());

        adapter.streamWithTools(systemPrompt, messages, tools, config)
                .doOnNext(chunk -> {
                    if (chunk.getType() == AiChunkType.TOOL_CALL) {
                        hasToolCall.set(true);
                        toolCallChunks.add(chunk);
                        sink.tryEmitNext(AiChunk.toolCall(chunk.getFunctionName(), chunk.getArguments()));
                    } else if (chunk.getType() == AiChunkType.TEXT) {
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
                    log.info("ReAct 第 {} 轮完成，hasToolCall={}", currentRound + 1, hasToolCall.get());

                    if (!hasToolCall.get()) {
                        sink.tryEmitNext(AiChunk.done());
                        sink.tryEmitComplete();
                        return;
                    }

                    if (currentRound + 1 >= MAX_ITERATIONS) {
                        sink.tryEmitNext(AiChunk.text("\n\n[已达到最大推理轮数]"));
                        sink.tryEmitNext(AiChunk.done());
                        sink.tryEmitComplete();
                        return;
                    }

                    // 执行工具，然后继续下一轮
                    for (AiChunk tc : toolCallChunks) {
                        executeTool(tc, handlers, messages, sink);
                    }

                    // 继续下一轮
                    runRound(adapter, systemPrompt, messages, tools, handlers, config, sink, iteration);
                })
                .subscribe();
    }

    /**
     * 执行工具调用，将结果注入对话历史
     */
    private void executeTool(AiChunk toolCall, Map<String, ToolHandler> handlers,
                              List<AiProviderAdapter.ChatMessage> messages,
                              Sinks.Many<AiChunk> sink) {
        String fnName = toolCall.getFunctionName();
        String fnArgs = toolCall.getArguments();
        log.info("执行工具: {}({})", fnName, fnArgs);

        ToolHandler handler = handlers.get(fnName);
        if (handler == null) {
            log.error("未知工具: {}", fnName);
            sink.tryEmitNext(AiChunk.error("未知工具: " + fnName));
            messages.add(new AiProviderAdapter.ChatMessage("assistant", "错误: 未知工具 " + fnName));
            return;
        }

        try {
            String result = handler.apply(fnArgs);
            log.info("工具 {} 执行完成，结果长度: {}", fnName, result != null ? result.length() : 0);
            sink.tryEmitNext(AiChunk.text("\n[工具 " + fnName + " 执行完成]\n"));

            messages.add(new AiProviderAdapter.ChatMessage("assistant",
                    "我调用了工具 " + fnName + " 来搜索信息。"));
            messages.add(new AiProviderAdapter.ChatMessage("user",
                    "工具 " + fnName + " 的返回结果:\n" + result + "\n请基于以上信息继续回答。"));
        } catch (Exception e) {
            log.error("工具 {} 执行失败: {}", fnName, e.getMessage(), e);
            String error = "工具 " + fnName + " 执行失败: " + e.getMessage();
            sink.tryEmitNext(AiChunk.error(error));
            messages.add(new AiProviderAdapter.ChatMessage("user", "错误: " + error));
        }
    }
}
