# Wiki 知识图谱 — 系统设计文档

> Phase 3c: 基于 Karpathy LLM Wiki 模式的知识图谱功能

## 1. 概述

### 1.1 背景

DevKnowledge 目前已有知识搜索、AI Demo 生成（ReAct Agent）、知识库 RAG 向量检索、Skills 构建四大模块。本次新增 **Wiki 知识图谱** 模块，参考 Karpathy 的 LLM Wiki 模式，构建一个由 LLM 增量维护的结构化知识库。

### 1.2 核心理念

Wiki 是一个**持久化、不断积累的知识产物**：
- LLM 读取原始文档，生成结构化的 wiki 页面（实体、概念、摘要）
- 自动提取实体关系，构建知识图谱
- 保留交叉引用、标注矛盾、补充缺失链接
- 知识只编译一次，后续持续维护，而非每次查询重新发现

### 1.3 与现有模块的关系

| 模块 | 关系 |
|------|------|
| 知识库 (KbPage) | 知识库文档可导入到 Wiki |
| Demo 生成 (DemoPage) | 新增 Wiki 检索源选项（RAG / Wiki / 无） |
| 知识搜索 (KnowledgePage) | 无直接关系，独立模块 |

## 2. 架构设计

### 2.1 整体架构

```
┌─────────────────────────────────────────────────────────┐
│                    前端 (React)                           │
│                                                          │
│  ┌──────────┐  ┌──────────┐  ┌──────────────────────┐   │
│  │ WikiPage │  │ Graph3D  │  │ DemoPage (已有)       │   │
│  │ 内容浏览  │  │ 3D 图谱  │  │ 新增 Wiki 检索源      │   │
│  └────┬─────┘  └────┬─────┘  └──────────────────────┘   │
│       │              │                                    │
└───────┼──────────────┼────────────────────────────────────┘
        │              │
┌───────┴──────────────┴────────────────────────────────────┐
│                    后端 (WebFlux)                          │
│                                                           │
│  WikiController                                           │
│    ├─ 上传/导入 → WikiIngestService → LLM 分析            │
│    ├─ 页面浏览 → WikiFileService → 读取 markdown 文件      │
│    ├─ 图谱数据 → WikiGraphService → 查询实体/关系          │
│    └─ Demo 检索 → WikiRetrievalService → index 定位页面    │
│                                                           │
│  ┌─────────────┐  ┌─────────────────────────────────────┐ │
│  │ PostgreSQL   │  │ 文件系统 (wiki vault)                │ │
│  │ wiki_documents│  │ vault/                              │ │
│  │ wiki_entities │  │ ├── index.md   (动态生成)           │ │
│  │ wiki_relations│  │ ├── log.md     (操作日志)           │ │
│  │ wiki_index    │  │ ├── entities/  (实体页面)           │ │
│  └─────────────┘  │ ├── concepts/  (概念页面)            │ │
│                    │ └── sources/   (来源摘要)            │ │
│                    └─────────────────────────────────────┘ │
└───────────────────────────────────────────────────────────┘
```

### 2.2 存储策略

| 内容 | 存储方式 | 说明 |
|------|----------|------|
| 原始文档 | PostgreSQL | wiki_documents 表存储内容和元数据 |
| Wiki 页面 | Markdown 文件 | 磁盘上，Obsidian 可直接打开 |
| 实体/关系 | PostgreSQL | 支撑 3D 图谱查询 |
| 索引元数据 | PostgreSQL | 支撑快速页面定位 |

### 2.3 索引策略

**双索引设计**：
- `wiki_index` 表：主存储，支撑快速查询和图谱聚合
- `index.md` 文件：Obsidian 兼容的可视化索引，由数据库同步生成

数据库是 source of truth，index.md 是衍生文件。

### 2.4 检索策略

Demo 生成时选择 Wiki 作为检索源，采用 **基于索引的检索**（非向量化）：
1. 查询 wiki_index 表获取页面目录
2. LLM 根据提示词定位相关页面
3. 读取页面文件内容注入上下文

## 3. 数据模型

### 3.1 数据库表

```sql
-- V10__create_wiki_tables.sql

-- Wiki 原始文档表
CREATE TABLE wiki_documents (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL,
    filename    VARCHAR(255) NOT NULL,
    file_type   VARCHAR(20) NOT NULL,        -- md/txt/pdf/docx
    file_size   BIGINT NOT NULL,
    content     TEXT,                         -- 原始内容
    status      VARCHAR(20) DEFAULT 'processing', -- processing/ready/error
    error_msg   TEXT,
    source_type VARCHAR(20) DEFAULT 'upload', -- upload/obsidian_vault/kb_import
    created_at  TIMESTAMPTZ DEFAULT now()
);

-- Wiki 实体表
CREATE TABLE wiki_entities (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL,
    name        VARCHAR(200) NOT NULL,        -- 实体名称
    type        VARCHAR(50) NOT NULL,         -- concept/framework/api/tool
    description TEXT,                         -- 简要描述
    page_path   VARCHAR(500),                 -- 对应的 wiki 页面文件路径
    doc_id      UUID,                         -- 来源文档
    created_at  TIMESTAMPTZ DEFAULT now(),
    updated_at  TIMESTAMPTZ DEFAULT now()
);

-- Wiki 关系表
CREATE TABLE wiki_relations (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL,
    source_id   UUID NOT NULL,               -- 源实体
    target_id   UUID NOT NULL,               -- 目标实体
    relation    VARCHAR(100) NOT NULL,        -- uses/extends/contradicts/related_to
    description TEXT,                         -- 关系描述
    strength    DOUBLE PRECISION DEFAULT 1.0, -- 关系强度 (LLM 评估)
    created_at  TIMESTAMPTZ DEFAULT now()
);

-- Wiki 索引表
CREATE TABLE wiki_index (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL,
    page_path   VARCHAR(500) NOT NULL,        -- wiki 页面文件路径
    title       VARCHAR(300) NOT NULL,
    category    VARCHAR(50) NOT NULL,         -- entity/concept/source/summary
    tags        TEXT[] DEFAULT '{}',
    summary     TEXT,                         -- 一句话摘要
    doc_ids     UUID[] DEFAULT '{}',          -- 关联的原始文档
    updated_at  TIMESTAMPTZ DEFAULT now()
);

-- 索引
CREATE INDEX idx_wiki_documents_user ON wiki_documents(user_id);
CREATE INDEX idx_wiki_documents_status ON wiki_documents(user_id, status);
CREATE INDEX idx_wiki_entities_user ON wiki_entities(user_id);
CREATE INDEX idx_wiki_entities_type ON wiki_entities(user_id, type);
CREATE INDEX idx_wiki_entities_doc ON wiki_entities(doc_id);
CREATE INDEX idx_wiki_relations_source ON wiki_relations(source_id);
CREATE INDEX idx_wiki_relations_target ON wiki_relations(target_id);
CREATE INDEX idx_wiki_index_user ON wiki_index(user_id);
CREATE INDEX idx_wiki_index_category ON wiki_index(user_id, category);
```

### 3.2 无外键约束

所有表不使用外键，引用完整性由 Service 层保证。

**删除策略**：
- 删除 wiki_document → 级联删除相关 entities → 级联删除相关 relations → 删除 index 条目
- 删除 wiki_entity → 级联删除相关 relations

### 3.3 关系强度

`wiki_relations.strength` 采用 LLM 直接评估（0.0-1.0），后续可叠加用户行为因子。

## 4. 文件结构

### 4.1 Wiki Vault 目录

```
wiki_vault/{user_id}/
├── index.md                         ← 动态生成的内容索引
├── log.md                           ← 操作日志（摄取/分析/查询）
├── entities/                        ← 实体页面
│   ├── react.md
│   ├── usestate.md
│   └── ...
├── concepts/                        ← 概念页面
│   ├── virtual-dom.md
│   ├── hooks-pattern.md
│   └── ...
├── sources/                         ← 来源摘要
│   ├── xxx-article-summary.md
│   └── ...
└── comparisons/                     ← 对比分析（可选）
    └── react-vs-vue.md
```

### 4.2 Wiki 页面格式

```markdown
---
type: entity
category: framework
tags: [frontend, ui, javascript]
sources: [doc-uuid-1, doc-uuid-2]
related: [virtual-dom, hooks-pattern]
created: 2026-05-29
updated: 2026-05-29
---

# React

> 用于构建用户界面的 JavaScript 库

## 核心概念
- [[virtual-dom]] — 虚拟 DOM 机制
- [[hooks-pattern]] — Hooks 模式

## 相关 API
- [[usestate]] — 状态管理
- [[useeffect]] — 副作用处理

## 来源
- [[xxx-article-summary]] — 来源文章摘要
```

### 4.3 index.md 格式

```markdown
# Wiki Index

## Entities
- [React](entities/react.md) — 用于构建用户界面的 JavaScript 库
- [Vue](entities/vue.md) — 渐进式 JavaScript 框架

## Concepts
- [Virtual DOM](concepts/virtual-dom.md) — 虚拟 DOM 机制
- [Hooks Pattern](concepts/hooks-pattern.md) — React Hooks 设计模式

## Sources
- [Article Title](sources/xxx-summary.md) — 来源文章摘要 (2026-05-29)
```

### 4.4 log.md 格式

```markdown
# Wiki Log

## [2026-05-29 10:30] ingest | Article Title
- 文档类型: markdown
- 生成页面: sources/article-title-summary.md
- 提取实体: React, Vue, Virtual DOM
- 建立关系: 3 条

## [2026-05-29 11:00] deep-analysis | Article Title
- 新增实体: Hooks Pattern
- 标注矛盾: 1 处
- 补充链接: 2 条
```

## 5. API 设计

### 5.1 WikiController 端点

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/wiki/upload` | 单文件上传 |
| POST | `/api/wiki/upload-vault` | Obsidian vault 目录上传 |
| POST | `/api/wiki/import/{kbId}` | 从知识库导入 |
| GET | `/api/wiki/pages` | 获取页面列表 |
| GET | `/api/wiki/page?path=xxx` | 读取 wiki 页面内容 |
| GET | `/api/wiki/index` | 获取 index 索引 |
| GET | `/api/wiki/graph` | 获取图谱数据（实体+关系） |
| POST | `/api/wiki/analyze/{docId}` | 手动触发深度分析 |
| DELETE | `/api/wiki/doc/{docId}` | 删除文档及相关 wiki 内容 |

### 5.2 图谱数据格式

```json
{
  "entities": [
    {
      "id": "uuid",
      "name": "React",
      "type": "framework",
      "description": "用于构建用户界面的 JavaScript 库",
      "pagePath": "entities/react.md"
    }
  ],
  "relations": [
    {
      "sourceId": "uuid-1",
      "targetId": "uuid-2",
      "relation": "uses",
      "description": "React 使用 Virtual DOM",
      "strength": 0.9
    }
  ]
}
```

## 6. 后端服务

### 6.1 Service 层职责

| Service | 职责 |
|---------|------|
| `WikiIngestService` | 文档摄取：解析文件 → 存储 → 调用 LLM 生成 wiki 页面 |
| `WikiLlmService` | LLM 交互：实体提取、关系识别、矛盾标注、页面生成 |
| `WikiFileService` | 文件操作：读写 markdown、管理 vault 目录结构 |
| `WikiGraphService` | 图谱查询：实体/关系 CRUD、图数据聚合 |
| `WikiRetrievalService` | Demo 检索：读 index → 定位相关页面 → 返回上下文 |

### 6.2 LLM 分析流程

**基础分析（摄取时自动触发）**：

```
文档上传
  → WikiIngestService 存入 wiki_documents
  → 调用 LLM（基础分析）
    → 生成 sources/xxx-summary.md
    → 更新 index.md
    → 写入 log.md
  → 返回状态
```

**深度分析（手动触发）**：

```
用户点击"深度分析"
  → WikiLlmService 读取文档内容
  → 调用 LLM（深度分析）
    → 提取实体 → 写入 wiki_entities + entities/*.md
    → 识别关系 → 写入 wiki_relations
    → 矛盾标注 → 更新相关页面
    → 补充链接 → 更新交叉引用
  → 更新 index.md + log.md
```

### 6.3 删除流程

```
DELETE /api/wiki/doc/{docId}
  → 查询 wiki_entities where doc_id = docId
  → 删除相关 wiki_relations (source_id/target_id in entity ids)
  → 删除 wiki_entities
  → 删除 wiki_index where doc_id in docIds
  → 删除 vault 中对应的 wiki 页面文件
  → 删除 wiki_documents
```

## 7. 前端设计

### 7.1 Wiki 页面布局

```
┌─────────────────────────────────────────────────────────────┐
│  Wiki 页面                                                    │
├──────────┬──────────────────────────────────────────────────┤
│          │                                                   │
│  侧边栏   │   主内容区                                        │
│          │                                                   │
│  ┌─────┐ │   ┌─────────────────────────────────────────┐    │
│  │ 上传 │ │   │                                         │    │
│  │ 文档 │ │   │         Wiki 页面内容                     │    │
│  └─────┘ │   │         (Markdown 渲染)                   │    │
│          │   │                                         │    │
│  ┌─────┐ │   │         实体/概念/来源 标签页              │    │
│  │ 页面 │ │   │                                         │    │
│  │ 列表 │ │   └─────────────────────────────────────────┘    │
│  │      │ │                                                   │
│  │ 实体  │ │   ┌─────────────────────────────────────────┐    │
│  │ 概念  │ │   │                                         │    │
│  │ 来源  │ │   │         3D 知识图谱                      │    │
│  │      │ │   │         (Three.js 粒子图)                │    │
│  └─────┘ │   │                                         │    │
│          │   └─────────────────────────────────────────┘    │
│          │                                                   │
└──────────┴──────────────────────────────────────────────────┘
```

### 7.2 核心交互

| 操作 | 说明 |
|------|------|
| 上传文档 | 支持单文件 + Obsidian vault 目录上传 |
| 浏览页面 | 侧边栏分类导航，点击渲染 markdown |
| 查看图谱 | 3D 粒子图谱，点击节点跳转对应页面 |
| Demo 检索 | 切换 Wiki 检索源，index 定位相关页面注入上下文 |

### 7.3 Demo 页面修改

现有 DemoPage 的检索源区域增加 Wiki 选项：

```
检索源: ○ RAG  ○ Wiki  ○ 无
```

- 选择 Wiki 时，调用 WikiRetrievalService 通过 index 定位相关页面
- Wiki 页面内容注入到 LLM 的 system prompt

### 7.4 首页入口

HomePage 的 modules 数组新增 Wiki 入口：

```typescript
{
  path: '/wiki',
  title: 'Wiki 知识图谱',
  desc: 'LLM 驱动的知识图谱，自动构建实体关系。',
  variation: 4,
}
```

## 8. 3D 知识图谱可视化

### 8.1 技术方案

- 使用项目已有的 Three.js + @react-three/fiber
- 节点 = 实体（大小由关系数量决定）
- 边 = 关系（粗细由 strength 决定）
- 力导向布局，节点可拖拽、悬浮高亮

### 8.2 节点类型颜色

| 实体类型 | 颜色 |
|---------|------|
| framework | 蓝色 |
| concept | 绿色 |
| api | 橙色 |
| tool | 紫色 |

### 8.3 交互

- 点击节点 → 跳转到对应 wiki 页面
- 悬浮节点 → 显示实体名称和描述
- 拖拽节点 → 调整图谱布局
- 缩放/旋转 → 3D 视角控制

## 9. 分阶段开发计划

| 阶段 | 内容 | 产出 |
|------|------|------|
| **3c-1** | 基础设施 | 数据库表 + Wiki 文件结构 + 单文件上传 + 页面浏览 |
| **3c-2** | LLM 摄取 | 摄取时自动生成 wiki 页面 + index + log |
| **3c-3** | 知识图谱 | 实体提取 + 关系识别 + 图谱数据 API |
| **3c-4** | 3D 可视化 | Three.js 粒子图谱 + 节点交互跳转 |
| **3c-5** | 高级功能 | Obsidian vault 上传 + 深度分析 + Demo Wiki 检索 + Lint |

**阶段依赖**：

```
3c-1 → 3c-2 → 3c-3 → 3c-4
                  ↘ 3c-5
```

每个阶段独立可交付。

## 10. 与 Karpathy LLM Wiki 模式的对应关系

| Karpathy 概念 | DevKnowledge 实现 |
|--------------|------------------|
| Raw sources | wiki_documents 表 |
| Wiki pages | vault/ 目录下的 markdown 文件 |
| Schema | LLM prompt 模板（WikiLlmService） |
| Ingest | WikiIngestService + POST /api/wiki/upload |
| Query | WikiRetrievalService + Demo Wiki 检索 |
| Lint | POST /api/wiki/analyze/{docId}（深度分析） |
| index.md | 动态生成的 wiki 索引 |
| log.md | 操作日志 |
| Obsidian graph view | 3D 粒子图谱（Three.js） |

## 11. 技术约束

- WebFlux 环境禁止阻塞调用，文件操作用 `Schedulers.boundedElastic()` 包装
- 数据库迁移文件使用 V10（现有 V1-V9）
- Wiki vault 目录路径：`wiki_vault/{user_id}/`
- API Key 加密存储（复用现有 AesUtil）
- 3D 图谱复用项目已有的 Three.js + @react-three/fiber
