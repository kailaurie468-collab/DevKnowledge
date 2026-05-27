# RAG 回测指标系统设计

> 日期：2026-05-27
> 状态：已批准
> Phase：1（自动采集 + 仪表盘），Phase 2 预留人工标注

## 背景

DemoService.java:97 的 TODO「RAG检测指标待构建」。当前 RAG 流程有预检索和二次检索，但没有指标衡量检索质量和生成质量。

## 目标

构建全链路 RAG 指标系统：
- **自动采集**：每次 Demo 生成时自动记录检索指标
- **前端仪表盘**：图表展示指标趋势和详情
- **Phase 2 预留**：人工标注接口（用户对 AI 回答评分）

---

## 一、数据模型

### 1.1 新建 `rag_metrics` 表（Flyway V9）

```sql
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

### 1.2 Phase 2 预留字段（本次不实现）

```sql
-- ALTER TABLE rag_metrics ADD COLUMN human_rating INT;
-- ALTER TABLE rag_metrics ADD COLUMN human_comment TEXT;
```

---

## 二、后端自动采集

### 2.1 采集指标定义

| 指标 | 来源 | 计算方式 |
|------|------|---------|
| `rag_used` | DemoService | req.getKbId() != null 且检索成功 |
| `top_k` | GenerateDemoRequest | req.getTopK() |
| `chunk_count` | contextChunks | chunks.size() |
| `avg_similarity` | contextChunks | chunks.stream().mapToDouble(score).average() |
| `max_similarity` | contextChunks | chunks.stream().mapToDouble(score).max() |
| `min_similarity` | contextChunks | chunks.stream().mapToDouble(score).min() |
| `retrieval_ms` | 计时 | searchKbVector 前后 System.currentTimeMillis() 差 |
| `tool_call_count` | ReActAgent | 统计 search_kb 工具被调用次数 |

### 2.2 采集流程

```
DemoService.generateDemo()
  ├─ 记录 startTime
  ├─ kbService.searchKbVector() → contextChunks
  ├─ 计算 retrievalMs = now - startTime
  ├─ 从 contextChunks 计算 avg/max/min similarity
  ├─ 构建 RagMetric 对象（不含 tool_call_count）
  ├─ 传递 RagMetric 给 ReActAgent.run()
  │   └─ Agent 内部统计 search_kb 调用次数
  └─ doOnComplete 中保存 ragMetric（含 tool_call_count）
```

### 2.3 API 端点

```
GET /api/user/rag-metrics
Authorization: Bearer <token>
Response: List<RagMetricResponse>

RagMetricResponse {
    demoId: UUID
    demoTitle: String
    kbId: UUID
    ragUsed: boolean
    topK: int
    chunkCount: int
    avgSimilarity: double
    maxSimilarity: double
    minSimilarity: double
    retrievalMs: int
    toolCallCount: int
    createdAt: Instant
}
```

返回近 7 天的 RAG 指标，按 created_at DESC 排序。

---

## 三、前端仪表盘

### 3.1 位置

SettingsPage 侧边栏新增「RAG 指标」tab，与 AI 配置、Embedding AI、数据存储并列。

### 3.2 展示内容

**指标概览卡片**（顶部三列）：
- 平均检索相似度（近 7 天均值）
- 平均检索耗时（近 7 天均值）
- RAG 使用率（rag_used=true 的记录占比）

**相似度趋势图**（柱状图）：
- X 轴：日期（近 7 天）
- Y 轴：avg_similarity
- 复用 AiSettings 的 Token 消耗柱状图样式

**检索详情表格**（底部）：
- 列：Demo 标题 | top-K | 命中数 | avg 相似度 | 耗时 | 时间
- 最近 20 条记录

### 3.3 改动范围

| 文件 | 改动 |
|------|------|
| `frontend/src/types/api.ts` | 新增 RagMetric 类型 |
| `frontend/src/api/settings.ts` | 新增 getRagMetrics() |
| `frontend/src/pages/settings/RagMetrics.tsx` | 新页面组件 |
| `frontend/src/pages/SettingsPage.tsx` | 侧边栏新增 tab |

---

## 四、Phase 2 预留（本次不实现）

- 人工标注 UI：Demo 详情页加评分组件
- 人工标注 API：PUT /api/demos/{id}/rating
- 合并展示：仪表盘同时展示自动指标 + 人工评分
- 问题诊断：自动分析"检索好但回答差"的情况
