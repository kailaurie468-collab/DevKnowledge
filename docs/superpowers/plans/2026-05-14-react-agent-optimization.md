# ReAct Agent 多轮推理优化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 ReAct Agent 从硬编码 3 轮改为动态可配置（默认 5 轮），加入提前终止和死循环检测，并优化 system prompt 引导模型自主控制工具调用节奏。

**Architecture:** 修改 `ReActAgent.run()` 接受 `maxIterations` 参数，新增提前终止逻辑（空结果、异常、死循环）。`DemoService` 透传配置并在 system prompt 中注入工具使用规则。`ChatMessage` record 扩展 `name` 字段支持标准 tool role 格式。

**Tech Stack:** Java 17, Spring Boot 3.3 WebFlux, Reactor (Sinks.Many), Lombok

---

### Task 1: 扩展 ChatMessage 支持 name 字段

**Files:**
- Modify: `backend/src/main/java/com/devknowledge/service/ai/AiProviderAdapter.java:43`

- [ ] **Step 1: 修改 ChatMessage record，新增带 name 的构造器**

将 `ChatMessage` 从二参数 record 改为三参数，`name` 字段可选（默认 null）：

```java
/**
 * 对话消息
 * @param role    角色：system / user / assistant / tool
 * @param content 消息内容
 * @param name    工具名（role=tool 时必填，其他情况为 null）
 */
record ChatMessage(String role, String content, String name) {
    ChatMessage(String role, String content) {
        this(role, content, null);
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `cd backend && mvn compile -q`
Expected: BUILD SUCCESS（现有代码调用 `new ChatMessage("user", msg)` 仍兼容二参数构造器）

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/devknowledge/service/ai/AiProviderAdapter.java
git commit -m "feat: extend ChatMessage record with optional name field"
```

---

### Task 2: GenerateDemoRequest 新增 maxIterations 字段

**Files:**
- Modify: `backend/src/main/java/com/devknowledge/dto/GenerateDemoRequest.java`

- [ ] **Step 1: 添加 maxIterations 字段**

在 `language` 字段下方添加：

```java
/** 最大推理轮数（可选，默认 5，范围 1-8） */
private Integer maxIterations;
```

- [ ] **Step 2: 编译验证**

Run: `cd backend && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/devknowledge/dto/GenerateDemoRequest.java
git commit -m "feat: add maxIterations field to GenerateDemoRequest"
```

---

### Task 3: 重写 ReActAgent — 动态轮数 + 提前终止 + 死循环检测

**Files:**
- Modify: `backend/src/main/java/com/devknowledge/service/ai/ReActAgent.java`

这是核心改动，一次性完成所有 ReActAgent 逻辑优化。

- [ ] **Step 1: 删除硬编码常量，run() 方法新增 maxIterations 参数**

替换整个 `ReActAgent.java` 文件：

```java
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

        runRound(adapter, systemPrompt, messages, tools, handlers, config, sink, iteration, effectiveMax, lastRoundSignatures, consecutiveAllFail);

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
                           AtomicInteger consecutiveAllFail) {

        int currentRound = iteration.getAndIncrement();
        log.info("ReAct 第 {} 轮开始，消息数: {}", currentRound + 1, messages.size());

        AtomicBoolean hasToolCall = new AtomicBoolean(false);
        AtomicBoolean hasError = new AtomicBoolean(false);
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
                    boolean allFailed = true;  // 本轮所有工具都失败（空或错误）

                    for (AiChunk tc : toolCallChunks) {
                        ToolResult result = executeTool(tc, handlers, messages, sink);
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
                        // 不 return，继续下一轮，模型看到错误后可能换策略或直接回答
                    } else {
                        consecutiveAllFail.set(0);  // 有成功结果，重置计数
                    }

                    // 更新死循环签名
                    lastRoundSignatures.clear();
                    lastRoundSignatures.addAll(currentSignatures);

                    // 继续下一轮
                    runRound(adapter, systemPrompt, messages, tools, handlers, config, sink,
                            iteration, maxIterations, lastRoundSignatures, consecutiveAllFail);
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
                                    Sinks.Many<AiChunk> sink) {
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
```

- [ ] **Step 2: 编译验证**

Run: `cd backend && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/devknowledge/service/ai/ReActAgent.java
git commit -m "feat: dynamic iterations, early termination, loop detection in ReActAgent"
```

---

### Task 4: DemoService 透传 maxIterations + 增强 system prompt

**Files:**
- Modify: `backend/src/main/java/com/devknowledge/service/DemoService.java:127`（reactAgent.run 调用处）
- Modify: `backend/src/main/java/com/devknowledge/service/DemoService.java:296-323`（buildSystemPrompt 方法）

- [ ] **Step 1: 修改 generateDemo() 传递 maxIterations**

找到 `DemoService.java` 第 127 行：

```java
return reactAgent.run(systemPrompt, req.getPrompt(), tools, handlers, config)
```

替换为：

```java
int maxIter = req.getMaxIterations() != null ? req.getMaxIterations() : 5;
return reactAgent.run(systemPrompt, req.getPrompt(), tools, handlers, config, maxIter)
```

- [ ] **Step 2: 增强 buildSystemPrompt() 添加工具使用规则**

在 `buildSystemPrompt` 方法的末尾（`prompt.append("- 解释要简洁...")` 之后）追加工具使用节奏控制：

```java
prompt.append("\n工具使用规则：\n");
prompt.append("- 你有以下工具可用：search_links（搜索框架文档链接）、get_framework_info（获取框架信息）\n");
prompt.append("- 优先用工具获取准确信息，不要凭记忆编造文档链接\n");
prompt.append("- 拿到足够信息后立即给出完整回答，不要重复调用相同工具\n");
prompt.append("- 如果工具返回空结果，直接基于已有知识回答\n");
```

- [ ] **Step 3: 编译验证**

Run: `cd backend && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/devknowledge/service/DemoService.java
git commit -m "feat: pass maxIterations to ReActAgent, enhance system prompt with tool rules"
```

---

### Task 5: OpenAiCompatibleAdapter 支持标准 tool role 格式

**Files:**
- Modify: `backend/src/main/java/com/devknowledge/service/ai/OpenAiCompatibleAdapter.java:59-63`（msgList 构建处）

- [ ] **Step 1: 修改 streamWithTools 中的消息列表构建**

将固定 `Map.of("role", "content")` 改为根据 `msg.name()` 判断：

找到 `OpenAiCompatibleAdapter.java` 第 59-63 行：

```java
List<Map<String, String>> msgList = new ArrayList<>();
msgList.add(Map.of("role", "system", "content", systemPrompt));
for (ChatMessage msg : messages) {
    msgList.add(Map.of("role", msg.role(), "content", msg.content()));
}
```

替换为：

```java
List<Map<String, String>> msgList = new ArrayList<>();
msgList.add(Map.of("role", "system", "content", systemPrompt));
for (ChatMessage msg : messages) {
    Map<String, String> msgMap = new LinkedHashMap<>();
    msgMap.put("role", msg.role());
    msgMap.put("content", msg.content());
    if (msg.name() != null) {
        msgMap.put("name", msg.name());
    }
    msgList.add(msgMap);
}
```

- [ ] **Step 2: 编译验证**

Run: `cd backend && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/devknowledge/service/ai/OpenAiCompatibleAdapter.java
git commit -m "feat: support tool role with name field in message list"
```

---

### Task 6: 端到端验证

- [ ] **Step 1: 启动后端，验证编译无误**

Run: `cd backend && mvn spring-boot:run`
Expected: 应用启动成功，无异常

- [ ] **Step 2: 测试默认轮数（5 轮）**

用 curl 或前端发起 Demo 生成请求（不传 maxIterations）：
```bash
curl -X POST http://localhost:8080/api/demos/generate \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"prompt": "React useEffect 用法", "language": "typescript"}'
```
Expected: SSE 流正常返回，日志显示 `maxIterations=5`

- [ ] **Step 3: 测试自定义轮数（1 轮）**

传入 `maxIterations=1`：
```bash
curl -X POST http://localhost:8080/api/demos/generate \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"prompt": "React hooks", "language": "typescript", "maxIterations": 1}'
```
Expected: 只执行 1 轮，即使模型想调工具也不继续

- [ ] **Step 4: 测试边界值（maxIterations=0 或 100）**

传入 `maxIterations=0` → 应自动修正为 1
传入 `maxIterations=100` → 应自动修正为 8

- [ ] **Step 5: Commit 最终状态**

```bash
git add -A
git commit -m "feat: ReAct Agent multi-round optimization complete"
```
