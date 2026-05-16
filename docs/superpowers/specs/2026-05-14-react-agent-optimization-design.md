# ReAct Agent 多轮推理优化设计

> 2026-05-14

## 背景

当前 `ReActAgent.java` 硬编码 `MAX_ITERATIONS = 3`，存在以下问题：
- 简单问题 1 轮就够，复杂问题可能需要 5-6 轮
- 工具返回空结果或报错时仍会浪费下一轮
- 没有死循环检测（模型反复调同一个工具）
- 对话历史拼装方式非标准，浪费 token

## 设计目标

1. 动态轮数：默认 5 轮，API 可配置（1-8）
2. Prompt 引导模型自主控制工具调用节奏
3. 提前终止：空结果、错误、死循环检测
4. 对话历史优化：对支持 `tool` role 的模型使用标准格式

## 改动范围

### 1. `ReActAgent.java` — 核心引擎

**动态轮数**：`run()` 方法新增 `int maxIterations` 参数，替代硬编码常量。

**提前终止逻辑**（在 `doOnComplete` 中，调用工具前判断）：
- 工具返回结果为空/blank → 跳过下一轮，直接让模型基于已有信息回答
- 工具执行抛异常 → 不进入下一轮
- 死循环检测：记录 `(fnName, args)` 对，连续两次相同 → 强制终止

**终止时**：追加 `[已达到最大推理轮数]` 或 `[工具调用异常，已停止推理]` 提示。

### 2. `OpenAiCompatibleAdapter.java` — 对话历史格式

新增 `supportsToolRole(String provider)` 判断：
- `openai` / `deepseek` → 使用标准 `role: "tool"` + `tool_call_id`
- `xiaomi` / `custom` → 保留当前 `assistant` + `user` 模拟方式

`ChatMessage` record 新增可选 `name` 字段（工具名）。

### 3. `DemoService.java` — 透传配置

从 `GenerateDemoRequest.maxIterations` 传入 `ReActAgent.run()`，默认 5。

### 4. `GenerateDemoRequest.java` — DTO

新增字段：
```java
private Integer maxIterations; // 可选，默认 5，范围 1-8
```

### 5. System Prompt 注入

在 `DemoService` 构建 system prompt 时，根据可用工具列表动态注入规则：
```
你有以下工具可用：{tool_descriptions}。
规则：
- 优先用工具获取准确信息，不要凭记忆编造
- 拿到足够信息后立即给出完整回答，不要重复调用相同工具
- 如果工具返回空结果，直接基于已有知识回答
```

## 不改动的部分

- `AiChunk` / `AiChunkType` — 数据结构不变
- `ToolHandler` — 工具执行接口不变
- `AiProviderAdapter` — 适配器接口不变（ChatMessage 扩展是向后兼容的）
- 前端 SSE 渲染逻辑 — 不变

## 验证方式

1. 简单问题（如"什么是 React"）→ 应在 1 轮内完成，不调工具
2. 中等问题（如"React useEffect 怎么用"）→ 1-2 轮，调 1 次 search_links
3. 复杂问题（如"对比 React 和 Vue 的 hooks 机制"）→ 2-3 轮，多次工具调用
4. 工具返回空 → 不继续浪费轮次
5. `maxIterations=1` → 只跑 1 轮，即使模型想调工具也不继续
