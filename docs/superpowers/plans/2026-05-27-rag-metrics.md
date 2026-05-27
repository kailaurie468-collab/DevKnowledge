# RAG 回测指标系统 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 构建 RAG 全链路指标系统：自动采集检索指标 + 前端仪表盘展示

**Architecture:** Flyway V9 新建 rag_metrics 表，DemoService 在 RAG 检索时自动采集指标（相似度、耗时、命中数），ReActAgent 通过共享 AtomicInteger 追踪 search_kb 调用次数，前端新增 RagMetrics 仪表盘页面展示趋势和详情。

**Tech Stack:** Java 17, Spring Boot 3.3 WebFlux, MyBatis Plus 3.5.7, PostgreSQL 16, React 19, TypeScript, Tailwind CSS v4

---

## 文件结构

| 文件 | 操作 | 职责 |
|------|------|------|
| `backend/src/main/resources/db/migration/V9__rag_metrics.sql` | 创建 | Flyway 迁移脚本 |
| `backend/src/main/java/com/devknowledge/model/RagMetric.java` | 创建 | 实体类 |
| `backend/src/main/java/com/devknowledge/mapper/RagMetricMapper.java` | 创建 | MyBatis Plus Mapper |
| `backend/src/main/java/com/devknowledge/dto/RagMetricResponse.java` | 创建 | API 响应 DTO |
| `backend/src/main/java/com/devknowledge/service/DemoService.java` | 修改 | 采集指标 + 保存 |
| `backend/src/main/java/com/devknowledge/service/ai/ReActAgent.java` | 修改 | 新增 search_kb 计数支持 |
| `backend/src/main/java/com/devknowledge/controller/SettingsController.java` | 修改 | 新增 rag-metrics 端点 |
| `frontend/src/types/api.ts` | 修改 | 新增 RagMetric 类型 |
| `frontend/src/api/settings.ts` | 修改 | 新增 getRagMetrics() |
| `frontend/src/pages/settings/RagMetrics.tsx` | 创建 | 仪表盘页面组件 |
| `frontend/src/pages/SettingsPage.tsx` | 修改 | 侧边栏新增 tab |

---

### Task 1: 数据库 + 实体 + Mapper

**Files:**
- Create: `backend/src/main/resources/db/migration/V9__rag_metrics.sql`
- Create: `backend/src/main/java/com/devknowledge/model/RagMetric.java`
- Create: `backend/src/main/java/com/devknowledge/mapper/RagMetricMapper.java`

- [ ] **Step 1: 创建 Flyway V9 迁移脚本**

```sql
-- V9__rag_metrics.sql
-- RAG 检索指标表：记录每次 Demo 生成时的 RAG 检索质量数据

CREATE TABLE rag_metrics (
    id              UUID PRIMARY KEY,
    demo_id         UUID UNIQUE REFERENCES demos(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users(id),
    kb_id           UUID NOT NULL,
    rag_used        BOOLEAN NOT NULL DEFAULT false,
    top_k           INT,
    chunk_count     INT,
    avg_similarity  DOUBLE PRECISION,
    max_similarity  DOUBLE PRECISION,
    min_similarity  DOUBLE PRECISION,
    retrieval_ms    INT,
    tool_call_count INT DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_rag_metrics_user_created ON rag_metrics(user_id, created_at DESC);
```

- [ ] **Step 2: 创建 RagMetric 实体类**

```java
package com.devknowledge.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * RAG 检索指标实体
 * 记录每次 Demo 生成时的 RAG 检索质量数据
 */
@Data
@TableName("rag_metrics")
public class RagMetric {

    @TableId(type = IdType.INPUT)
    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID id;

    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID demoId;

    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID userId;

    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID kbId;

    /** 是否使用了 RAG */
    private Boolean ragUsed;

    /** 检索配置的 top-K */
    private Integer topK;

    /** 实际命中 chunk 数 */
    private Integer chunkCount;

    /** top-K 平均相似度 */
    private Double avgSimilarity;

    /** 最高相似度 */
    private Double maxSimilarity;

    /** 最低相似度 */
    private Double minSimilarity;

    /** 检索耗时（毫秒） */
    private Integer retrievalMs;

    /** search_kb 工具被调用次数 */
    private Integer toolCallCount;

    private Instant createdAt;
}
```

- [ ] **Step 3: 创建 RagMetricMapper**

```java
package com.devknowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devknowledge.model.RagMetric;
import org.apache.ibatis.annotations.Mapper;

/**
 * RAG 指标 Mapper
 */
@Mapper
public interface RagMetricMapper extends BaseMapper<RagMetric> {
}
```

- [ ] **Step 4: 编译验证**

Run: `cd /d/DevKnowledge/backend && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/resources/db/migration/V9__rag_metrics.sql \
       backend/src/main/java/com/devknowledge/model/RagMetric.java \
       backend/src/main/java/com/devknowledge/mapper/RagMetricMapper.java
git commit -m "feat(rag): V9 迁移 + RagMetric 实体 + Mapper"
```

---

### Task 2: ReActAgent 支持工具调用计数

**Files:**
- Modify: `backend/src/main/java/com/devknowledge/service/ai/ReActAgent.java`

- [ ] **Step 1: 添加带 toolCallCount 参数的 run() 重载**

在 ReActAgent.java 的现有 `run()` 方法（第 51 行）之后，添加新的重载方法。核心思路：DemoService 传入一个 `AtomicInteger`，Agent 内部每次执行 search_kb 时递增。

```java
/**
 * 运行 ReAct 循环（带工具调用计数器）
 *
 * @param toolCallCount 外部传入的计数器，Agent 会在每次 search_kb 调用时递增
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
    List<String> lastRoundSignatures = Collections.synchronizedList(new ArrayList<>());
    AtomicInteger consecutiveAllFail = new AtomicInteger(0);

    runRound(adapter, systemPrompt, messages, tools, handlers, config, sink,
            iteration, effectiveMax, lastRoundSignatures, consecutiveAllFail, toolCallCounts);

    return sink.asFlux();
}
```

- [ ] **Step 2: 修改 runRound() 签名，接收 toolCallCounts**

修改现有的 `runRound()` 方法签名（第 77 行），添加 `Map<String, AtomicInteger> toolCallCounts` 参数：

```java
private void runRound(AiProviderAdapter adapter, String systemPrompt,
                       List<AiProviderAdapter.ChatMessage> messages,
                       List<AiFunction> tools, Map<String, ToolHandler> handlers,
                       UserAiConfig config, Sinks.Many<AiChunk> sink,
                       AtomicInteger iteration, int maxIterations,
                       List<String> lastRoundSignatures,
                       AtomicInteger consecutiveAllFail,
                       Map<String, AtomicInteger> toolCallCounts) {
```

- [ ] **Step 3: 在 executeTool() 中记录工具调用次数**

修改 `executeTool()` 方法，在工具执行成功后递增计数器。在 `executeTool()` 签名中添加 `Map<String, AtomicInteger> toolCallCounts` 参数。

在 `handler.apply(fnArgs)` 成功后（第 229 行之后）添加：

```java
// 记录工具调用次数
if (toolCallCounts != null) {
    toolCallCounts.computeIfAbsent(fnName, k -> new AtomicInteger(0)).incrementAndGet();
}
```

- [ ] **Step 4: 传递 toolCallCounts 到递归调用**

修改 `runRound()` 中的递归调用（约第 200 行）和 `executeTool()` 调用（约第 172 行），传递 `toolCallCounts` 参数。

- [ ] **Step 5: 保留旧的 run() 方法签名不变**

确保现有的 `run(systemPrompt, userMessage, tools, handlers, config, maxIterations)` 方法仍然可用（不传 toolCallCounts 时调用新方法传 null）：

```java
public Flux<AiChunk> run(String systemPrompt, String userMessage,
                          List<AiFunction> tools, Map<String, ToolHandler> handlers,
                          UserAiConfig config, int maxIterations) {
    return run(systemPrompt, userMessage, tools, handlers, config, maxIterations, null);
}
```

- [ ] **Step 6: 编译验证**

Run: `cd /d/DevKnowledge/backend && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/devknowledge/service/ai/ReActAgent.java
git commit -m "feat(agent): ReActAgent 支持工具调用次数追踪"
```

---

### Task 3: DemoService 指标采集 + 保存

**Files:**
- Modify: `backend/src/main/java/com/devknowledge/service/DemoService.java`
- Create: `backend/src/main/java/com/devknowledge/dto/RagMetricResponse.java`

- [ ] **Step 1: 创建 RagMetricResponse DTO**

```java
package com.devknowledge.dto;

import lombok.Data;
import java.time.Instant;
import java.util.UUID;

/**
 * RAG 指标响应 DTO
 */
@Data
public class RagMetricResponse {
    private UUID demoId;
    private String demoTitle;
    private UUID kbId;
    private Boolean ragUsed;
    private Integer topK;
    private Integer chunkCount;
    private Double avgSimilarity;
    private Double maxSimilarity;
    private Double minSimilarity;
    private Integer retrievalMs;
    private Integer toolCallCount;
    private Instant createdAt;
}
```

- [ ] **Step 2: 在 DemoService 添加 RagMetricMapper 注入**

在 DemoService 类的字段区域（现有的 mapper 注入之后）添加：

```java
private final RagMetricMapper ragMetricMapper;
```

确保构造函数或 `@RequiredArgsConstructor` 能自动注入。

- [ ] **Step 3: 在 generateDemo() 中采集 RAG 指标**

修改 `generateDemo()` 方法中的 RAG 预检索部分（第 96-112 行）。替换为：

```java
// RAG 预检索注入 + 指标采集
RagMetric ragMetric = null;
Map<String, AtomicInteger> toolCallCounts = new HashMap<>();

if (req.getKbId() != null) {
    int topK = req.getTopK() != null ? req.getTopK() : 3;
    ragMetric = new RagMetric();
    ragMetric.setId(UUID.randomUUID());
    ragMetric.setUserId(userId);
    ragMetric.setKbId(req.getKbId());
    ragMetric.setTopK(topK);
    ragMetric.setToolCallCount(0);
    ragMetric.setCreatedAt(Instant.now());

    try {
        long startTime = System.currentTimeMillis();
        List<KbChunkSearchResult> contextChunks =
                kbService.searchKbVector(userId, req.getKbId(), req.getPrompt(), topK).block();
        long retrievalMs = System.currentTimeMillis() - startTime;

        if (contextChunks != null && !contextChunks.isEmpty()) {
            systemPrompt += buildRagContext(contextChunks);

            // 计算相似度指标
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
    } catch (Exception e) {
        log.warn("RAG 预检索失败，继续无 RAG 生成: {}", e.getMessage());
        ragMetric.setRagUsed(false);
    }

    tools.add(toolProvider.getKbTool());
    handlers.put("search_kb", toolProvider.getKbHandler(userId, req.getKbId()));
}
```

- [ ] **Step 4: 修改 reactAgent.run() 调用，传递 toolCallCounts**

将现有的 `reactAgent.run(...)` 调用（第 118 行）改为使用带 toolCallCounts 的重载：

```java
int maxIter = req.getMaxIterations() != null ? req.getMaxIterations() : 5;
final RagMetric finalRagMetric = ragMetric;
return reactAgent.run(systemPrompt, req.getPrompt(), tools, handlers, config, maxIter, toolCallCounts)
```

- [ ] **Step 5: 在 doOnComplete 中保存 ragMetric**

修改 `doOnComplete` 回调（第 143 行），在保存 Demo 之后保存 ragMetric：

```java
.doOnComplete(() -> {
    // 流式输出完成后，保存 Demo 到数据库
    String fullOutput = outputCollector.toString();
    if (!fullOutput.isEmpty() && userId != null) {
        saveDemoSync(userId, req, fullOutput);
    }

    // 保存 RAG 指标
    if (finalRagMetric != null && userId != null) {
        try {
            // 获取 search_kb 调用次数
            AtomicInteger kbCount = toolCallCounts.get("search_kb");
            finalRagMetric.setToolCallCount(kbCount != null ? kbCount.get() : 0);
            ragMetricMapper.insert(finalRagMetric);
            log.info("RAG 指标已保存: demoId={}, avgSim={}", finalRagMetric.getDemoId(), finalRagMetric.getAvgSimilarity());
        } catch (Exception e) {
            log.warn("保存 RAG 指标失败: {}", e.getMessage());
        }
    }
});
```

- [ ] **Step 6: 移除旧的 TODO 注释**

删除第 97 行的 `//TODO: RAG检测指标待构建`

- [ ] **Step 7: 添加必要的 import**

```java
import com.devknowledge.mapper.RagMetricMapper;
import com.devknowledge.model.RagMetric;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
```

- [ ] **Step 8: 编译验证**

Run: `cd /d/DevKnowledge/backend && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/com/devknowledge/service/DemoService.java \
       backend/src/main/java/com/devknowledge/dto/RagMetricResponse.java
git commit -m "feat(rag): DemoService 自动采集 RAG 检索指标"
```

---

### Task 4: 后端 API 端点

**Files:**
- Modify: `backend/src/main/java/com/devknowledge/controller/SettingsController.java`

- [ ] **Step 1: 添加 getRagMetrics API 端点**

在 SettingsController.java 的 Token 消耗统计区域之后（第 117 行之后），添加：

```java
// ==================== RAG 指标统计 ====================

/**
 * 获取近 7 天 RAG 检索指标
 */
@GetMapping("/user/rag-metrics")
public Mono<ResponseEntity<List<RagMetricResponse>>> getRagMetrics(
        @RequestHeader("Authorization") String authHeader) {
    UUID userId = extractUserId(authHeader);
    return Mono.fromCallable(() -> demoService.getRagMetrics(userId))
            .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
            .map(ResponseEntity::ok);
}
```

- [ ] **Step 2: 在 DemoService 添加 getRagMetrics() 方法**

在 DemoService.java 中添加查询方法：

```java
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

    // 关联 Demo 标题
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
```

- [ ] **Step 3: 添加必要的 import**

```java
import com.devknowledge.dto.RagMetricResponse;
import com.devknowledge.model.RagMetric;
```

- [ ] **Step 4: 编译验证**

Run: `cd /d/DevKnowledge/backend && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/devknowledge/controller/SettingsController.java \
       backend/src/main/java/com/devknowledge/service/DemoService.java
git commit -m "feat(rag): GET /api/user/rag-metrics 端点"
```

---

### Task 5: 前端类型 + API 客户端

**Files:**
- Modify: `frontend/src/types/api.ts`
- Modify: `frontend/src/api/settings.ts`

- [ ] **Step 1: 在 api.ts 添加 RagMetric 类型**

在 `EmbeddingConfigRequest` 接口之后（第 233 行之后）添加：

```typescript
// RAG Metrics
export interface RagMetric {
  demoId: string
  demoTitle: string
  kbId: string
  ragUsed: boolean
  topK: number
  chunkCount: number
  avgSimilarity: number
  maxSimilarity: number
  minSimilarity: number
  retrievalMs: number
  toolCallCount: number
  createdAt: string
}
```

- [ ] **Step 2: 在 settings.ts 添加 getRagMetrics()**

```typescript
import type { AiConfig, AiConfigRequest, ProviderInfo, TokenUsage, RagMetric } from '@/types/api'
```

在 `settingsApi` 对象末尾添加：

```typescript
getRagMetrics: () =>
  api.get<RagMetric[]>('/user/rag-metrics'),
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/types/api.ts frontend/src/api/settings.ts
git commit -m "feat(rag): 前端 RagMetric 类型 + API 客户端"
```

---

### Task 6: RagMetrics 仪表盘页面

**Files:**
- Create: `frontend/src/pages/settings/RagMetrics.tsx`

- [ ] **Step 1: 创建 RagMetrics 组件**

参考 AiSettings 中的 Token 消耗柱状图样式，创建 RAG 指标仪表盘：

```tsx
import { useState, useEffect } from 'react'
import { settingsApi } from '@/api/settings'
import type { RagMetric } from '@/types/api'

export function RagMetrics() {
  const [metrics, setMetrics] = useState<RagMetric[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    setLoading(true)
    settingsApi.getRagMetrics()
      .then(setMetrics)
      .catch(console.error)
      .finally(() => setLoading(false))
  }, [])

  // 计算概览指标
  const ragUsedMetrics = metrics.filter(m => m.ragUsed)
  const avgSimilarity = ragUsedMetrics.length > 0
    ? ragUsedMetrics.reduce((sum, m) => sum + (m.avgSimilarity || 0), 0) / ragUsedMetrics.length
    : 0
  const avgRetrievalMs = ragUsedMetrics.length > 0
    ? ragUsedMetrics.reduce((sum, m) => sum + (m.retrievalMs || 0), 0) / ragUsedMetrics.length
    : 0
  const ragUsageRate = metrics.length > 0
    ? (ragUsedMetrics.length / metrics.length) * 100
    : 0

  // 按日期聚合相似度（用于柱状图）
  const dailySimilarity = new Map<string, number[]>()
  ragUsedMetrics.forEach(m => {
    const date = m.createdAt.slice(0, 10)
    if (!dailySimilarity.has(date)) dailySimilarity.set(date, [])
    dailySimilarity.get(date)!.push(m.avgSimilarity || 0)
  })
  const chartData = Array.from(dailySimilarity.entries())
    .map(([date, values]) => ({
      date,
      avg: values.reduce((a, b) => a + b, 0) / values.length,
    }))
    .sort((a, b) => a.date.localeCompare(b.date))
    .slice(-7)
  const maxChartValue = Math.max(...chartData.map(d => d.avg), 0.01)

  if (loading) {
    return <p className="text-gray-400 text-sm py-8 text-center">加载中...</p>
  }

  return (
    <div>
      <h2 className="text-lg font-medium text-gray-900 mb-6">RAG 检索指标</h2>

      {/* 概览卡片 */}
      <div className="grid grid-cols-3 gap-4 mb-6">
        <div className="border border-gray-200 rounded-lg p-4">
          <p className="text-xs text-gray-500">平均检索相似度</p>
          <p className="text-2xl font-bold text-gray-900 mt-1">
            {(avgSimilarity * 100).toFixed(1)}%
          </p>
        </div>
        <div className="border border-gray-200 rounded-lg p-4">
          <p className="text-xs text-gray-500">平均检索耗时</p>
          <p className="text-2xl font-bold text-gray-900 mt-1">
            {avgRetrievalMs.toFixed(0)}ms
          </p>
        </div>
        <div className="border border-gray-200 rounded-lg p-4">
          <p className="text-xs text-gray-500">RAG 使用率</p>
          <p className="text-2xl font-bold text-gray-900 mt-1">
            {ragUsageRate.toFixed(0)}%
          </p>
        </div>
      </div>

      {/* 相似度趋势图 */}
      <div className="border border-gray-200 rounded-lg p-6 mb-6">
        <h3 className="text-sm font-medium text-gray-500 mb-4">相似度趋势（近 7 天）</h3>
        {chartData.length === 0 ? (
          <p className="text-sm text-gray-400 py-8 text-center">暂无数据</p>
        ) : (
          <div className="flex items-end gap-2 h-40">
            {chartData.map((d, i) => {
              const height = maxChartValue > 0 ? (d.avg / maxChartValue) * 100 : 0
              return (
                <div key={i} className="flex-1 flex flex-col items-center gap-1 group">
                  <span className="text-xs text-gray-400 opacity-0 group-hover:opacity-100 transition-opacity">
                    {(d.avg * 100).toFixed(1)}%
                  </span>
                  <div className="w-full flex items-end" style={{ height: '120px' }}>
                    <div
                      className="w-full bg-green-500 rounded-t transition-all group-hover:bg-green-600"
                      style={{ height: `${Math.max(height, 2)}%` }}
                    />
                  </div>
                  <span className="text-xs text-gray-500">{d.date.slice(5)}</span>
                </div>
              )
            })}
          </div>
        )}
      </div>

      {/* 检索详情表格 */}
      <div className="border border-gray-200 rounded-lg p-6">
        <h3 className="text-sm font-medium text-gray-500 mb-4">检索详情（最近 20 条）</h3>
        {ragUsedMetrics.length === 0 ? (
          <p className="text-sm text-gray-400 py-8 text-center">暂无 RAG 检索记录</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-gray-500 border-b">
                  <th className="pb-2 pr-4">Demo</th>
                  <th className="pb-2 pr-4">top-K</th>
                  <th className="pb-2 pr-4">命中数</th>
                  <th className="pb-2 pr-4">相似度</th>
                  <th className="pb-2 pr-4">耗时</th>
                  <th className="pb-2">工具调用</th>
                </tr>
              </thead>
              <tbody>
                {ragUsedMetrics.slice(0, 20).map((m, i) => (
                  <tr key={i} className="border-b border-gray-100">
                    <td className="py-2 pr-4 max-w-[200px] truncate">{m.demoTitle}</td>
                    <td className="py-2 pr-4">{m.topK}</td>
                    <td className="py-2 pr-4">{m.chunkCount}</td>
                    <td className="py-2 pr-4">{((m.avgSimilarity || 0) * 100).toFixed(1)}%</td>
                    <td className="py-2 pr-4">{m.retrievalMs}ms</td>
                    <td className="py-2">{m.toolCallCount}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  )
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/pages/settings/RagMetrics.tsx
git commit -m "feat(rag): RagMetrics 仪表盘页面组件"
```

---

### Task 7: SettingsPage 侧边栏集成

**Files:**
- Modify: `frontend/src/pages/SettingsPage.tsx`

- [ ] **Step 1: 添加 RagMetrics tab**

修改 `SettingsPage.tsx`，在 tabs 数组中新增 RAG 指标 tab，导入 RagMetrics 组件：

```tsx
import { RagMetrics } from './settings/RagMetrics'

type SettingsTab = 'ai' | 'embedding' | 'rag' | 'storage'

const tabs: { key: SettingsTab; label: string; desc: string }[] = [
  { key: 'ai', label: 'AI 服务配置', desc: 'Chat 模型配置' },
  { key: 'embedding', label: 'Embedding AI', desc: '文本向量化模型' },
  { key: 'rag', label: 'RAG 指标', desc: '检索质量监控' },
  { key: 'storage', label: '数据存储', desc: '本地存储设置' },
]
```

在内容区（`<div className="flex-1 min-w-0">`）中添加：

```tsx
{activeTab === 'rag' && <RagMetrics />}
```

- [ ] **Step 2: 编译验证**

Run: `cd /d/DevKnowledge/backend && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add frontend/src/pages/SettingsPage.tsx
git commit -m "feat(rag): SettingsPage 侧边栏新增 RAG 指标 tab"
```
