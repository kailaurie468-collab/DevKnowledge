# DevKnowledge - 开发者知识平台

## Context

全栈开发者需要一个平台，解决三个痛点：
1. 开发时查文档要到处找，且希望直接跳转到具体知识点
2. 开发时需要某个知识点的 demo 示例，带解释
3. 个人开发习惯和工作流无法沉淀为可复用的 skills

目标用户：使用 Android、React、Java Spring 等框架的开发者。

---

## 技术架构

```
React SPA (Vite, port 5173) ←→ Spring Boot WebFlux (port 8080) ←→ PostgreSQL (port 5432)
                                      ↓                        ↕
                                ReAct Agent Engine        pgvector
                                      ↓                   (语义搜索)
                              AiProviderAdapter
                                      ↓
                              用户自定义 AI API
                              (OpenAI / Claude / DeepSeek / ...)
```

| 层 | 选型 | 理由 |
|---|---|---|
| 前端 | Vite + React 19 + TypeScript + Tailwind | SPA 够用，不需要 SSR |
| 后端 | Spring Boot 3.3 + WebFlux | 响应式流天然支持 SSE streaming |
| 数据库 | PostgreSQL 16 + pgvector | 全文搜索 (tsvector)、JSONB、数组类型、向量存储 |
| AI | 用户自定义（支持 Claude / OpenAI / DeepSeek 等） | 用户自己配置 API Key 和服务商 |
| AI 编排 | Spring AI + ReAct Agent | 原生 Java RAG 支持、多模型适配、Embedding 集成、Function Calling |
| AI 推理模式 | ReAct (Reasoning + Acting) | AI 自主决定工具调用策略，多轮推理优于单次 prompt |
| AI 结构化输出 | Function Calling | 保证 JSON Schema 合规输出，消除解析重试 |
| 认证 | Spring Security + JWT | 标准方案 |

---

## AI API 自定义设计

### 核心理念

平台不内置任何 AI 服务，用户在个人设置中配置自己的 AI Provider。

### 用户配置项

| 字段 | 说明 | 示例 |
|---|---|---|
| provider | AI 服务商类型 | `openai` / `anthropic` / `deepseek` / `custom` |
| api_key | 用户的 API Key | `sk-xxx...` |
| base_url | API 基础地址 | `https://api.openai.com/v1` 或自定义地址 |
| model | 使用的模型名 | `gpt-4o` / `claude-sonnet-4-20250514` / `deepseek-chat` |
| max_tokens | 最大输出 token 数 | `4096` |

### 数据库表：`user_ai_configs`

| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PK |
| user_id | UUID | FK -> users(id), UNIQUE |
| provider | VARCHAR(50) | NOT NULL |
| api_key | VARCHAR(500) | NOT NULL (加密存储) |
| base_url | VARCHAR(500) | NOT NULL |
| model | VARCHAR(100) | NOT NULL |
| max_tokens | INTEGER | DEFAULT 4096 |
| created_at | TIMESTAMPTZ | NOT NULL |
| updated_at | TIMESTAMPTZ | NOT NULL |

### API 接口

```
GET    /api/user/ai-config              — 获取当前用户的 AI 配置（api_key 脱敏返回）
PUT    /api/user/ai-config              — 更新 AI 配置
POST   /api/user/ai-config/test         — 测试配置是否可用（发送一个简单请求验证）
GET    /api/providers                    — 获取支持的 provider 列表及其默认 base_url
```

### 后端适配层

后端实现统一的 `AiProviderAdapter` 接口，适配不同服务商的 API 格式。支持两种调用模式：

**1. 普通流式对话** — 直接返回文本流
**2. Function Calling（工具调用）** — AI 输出结构化函数调用，后端执行后返回结果，AI 继续推理（ReAct 循环）

```java
public interface AiProviderAdapter {
    // 普通流式对话
    Flux<String> streamCompletion(String systemPrompt, String userMessage, AiConfig config);

    // 支持 Function Calling 的流式对话
    Flux<AiChunk> streamWithTools(String systemPrompt, String userMessage,
        List<AiFunction> tools, AiConfig config);
}

// AI 输出块 — 可以是文本、函数调用请求、或最终完成
public record AiChunk(
    AiChunkType type,       // TEXT / TOOL_CALL / DONE
    String content,         // 文本内容（type=TEXT 时）
    String functionName,    // 函数名（type=TOOL_CALL 时）
    String arguments        // JSON 参数（type=TOOL_CALL 时）
) {}

// 定义 AI 可调用的工具
public record AiFunction(
    String name,
    String description,
    String parametersJsonSchema  // JSON Schema 格式
) {}
```

具体实现：
- `OpenAiAdapter` — 兼容 OpenAI 格式（OpenAI、DeepSeek、Moonshot、通义千问等国内兼容服务），使用 `tools` 参数
- `AnthropicAdapter` — Claude API 格式，使用 `tool_use` blocks
- `CustomAdapter` — 用户自定义 base_url，使用 OpenAI 兼容格式（大多数国产大模型都兼容）

> **兼容性说明：** 不是所有模型都支持 Function Calling。后端会检测模型能力，不支持时自动降级为纯 prompt 方式（Skill 提取退化为 JSON 文本解析，Demo 生成退化为手动注入 RAG 上下文）。

前端设置页提供 Provider 下拉选择，选择后自动填充默认 base_url，用户只需填 API Key 和选择模型。

---

## 数据库设计（7 张核心表）

### users
| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PK |
| email | VARCHAR(255) | UNIQUE, NOT NULL |
| password_hash | VARCHAR(255) | NOT NULL |
| display_name | VARCHAR(100) | |
| created_at | TIMESTAMPTZ | NOT NULL |
| updated_at | TIMESTAMPTZ | NOT NULL |

### user_ai_configs
（见上方 AI 自定义设计）

### frameworks
| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PK |
| name | VARCHAR(100) | NOT NULL, UNIQUE |
| slug | VARCHAR(100) | NOT NULL, UNIQUE |
| base_url | VARCHAR(500) | NOT NULL |
| icon_url | VARCHAR(500) | |
| description | TEXT | |
| category | VARCHAR(50) | NOT NULL (frontend/backend/mobile) |
| created_at | TIMESTAMPTZ | NOT NULL |

### knowledge_links
| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PK |
| framework_id | UUID | FK -> frameworks(id) |
| title | VARCHAR(300) | NOT NULL |
| url | VARCHAR(1000) | NOT NULL |
| anchor | VARCHAR(200) | 锚点，用于深链接 |
| description | TEXT | |
| tags | TEXT[] | PostgreSQL 数组 |
| search_vector | TSVECTOR | GIN 索引 |
| popularity_score | INTEGER | DEFAULT 0 |
| created_at | TIMESTAMPTZ | NOT NULL |
| updated_at | TIMESTAMPTZ | NOT NULL |

### demos
| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PK |
| user_id | UUID | FK -> users(id), nullable |
| title | VARCHAR(300) | NOT NULL |
| prompt | TEXT | NOT NULL |
| framework_id | UUID | FK, nullable |
| code_content | TEXT | NOT NULL |
| explanation | TEXT | NOT NULL |
| language | VARCHAR(50) | NOT NULL |
| tags | TEXT[] | |
| tokens_used | INTEGER | |
| model_version | VARCHAR(50) | 记录用的哪个模型 |
| created_at | TIMESTAMPTZ | NOT NULL |

### skills
| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PK |
| user_id | UUID | FK -> users(id), NOT NULL |
| name | VARCHAR(200) | NOT NULL |
| description | TEXT | NOT NULL |
| category | VARCHAR(50) | |
| framework_id | UUID | FK, nullable |
| trigger_description | TEXT | NOT NULL |
| exported_content | TEXT | Claude Code .md 内容 |
| version | INTEGER | DEFAULT 1 |
| is_public | BOOLEAN | DEFAULT false |
| created_at | TIMESTAMPTZ | NOT NULL |
| updated_at | TIMESTAMPTZ | NOT NULL |

### skill_steps
| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PK |
| skill_id | UUID | FK -> skills(id) ON DELETE CASCADE |
| step_order | INTEGER | NOT NULL |
| title | VARCHAR(200) | NOT NULL |
| description | TEXT | NOT NULL |
| step_type | VARCHAR(30) | action/decision/validation/reference |
| code_template | TEXT | |
| expected_output | TEXT | |
| notes | TEXT | |

UNIQUE(skill_id, step_order)

### knowledge_bases
| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PK |
| user_id | UUID | FK -> users(id), NOT NULL |
| name | VARCHAR(200) | NOT NULL |
| description | TEXT | |
| created_at | TIMESTAMPTZ | NOT NULL |
| updated_at | TIMESTAMPTZ | NOT NULL |

### kb_documents
| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PK |
| kb_id | UUID | FK -> knowledge_bases(id) ON DELETE CASCADE |
| filename | VARCHAR(300) | NOT NULL |
| content | TEXT | NOT NULL |
| file_type | VARCHAR(20) | NOT NULL (md/txt) |
| chunk_count | INTEGER | DEFAULT 0 |
| created_at | TIMESTAMPTZ | NOT NULL |

### kb_chunks
| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PK |
| document_id | UUID | FK -> kb_documents(id) ON DELETE CASCADE |
| kb_id | UUID | FK -> knowledge_bases(id) ON DELETE CASCADE |
| chunk_index | INTEGER | NOT NULL |
| content | TEXT | NOT NULL |
| embedding | VECTOR(1536) | pgvector 向量 |
| created_at | TIMESTAMPTZ | NOT NULL |

CREATE INDEX ON kb_chunks USING ivfflat (embedding vector_cosine_ops);

### 用户行为数据存储

行为数据支持两种存储模式，用户在设置页自选：

#### 模式 1：本地文件存储（File System Access API，默认推荐）

```
用户操作 → 前端提取关键词摘要 → 写入本地 JSONL 文件
    ↓
触发推荐时 → 前端读取本地文件 → 前端聚类 → 发送摘要到后端
    ↓
后端调用 AI → 返回推荐 Skill
```

**技术实现：**

```javascript
// 前端 ActivityStorage — File System Access API
class LocalActivityStorage {
  private dirHandle: FileSystemDirectoryHandle | null = null

  // 用户授权选择目录（只需一次，浏览器持久记住）
  async init(): Promise<boolean> {
    try {
      this.dirHandle = await window.showDirectoryPicker({ mode: 'readwrite' })
      // 保存目录名称到 IndexedDB 以便下次自动恢复权限
      const db = await openDB('devknowledge', 1)
      await db.put('config', this.dirHandle.name, 'activityDirName')
      return true
    } catch {
      return false
    }
  }

  // 恢复已授权的目录（页面刷新后）
  async restore(): Promise<boolean> {
    const db = await openDB('devknowledge', 1)
    const dirName = await db.get('config', 'activityDirName')
    if (!dirName) return false
    // 浏览器会自动检查是否仍有权限
    // 如果权限过期，会在首次写入时提示用户重新授权
    return true
  }

  // 追加一条活动记录（只存摘要，不存原始 prompt）
  async record(activity: ActivitySummary): Promise<void> {
    const date = new Date().toISOString().slice(0, 10) // 2026-05-01
    const fileName = `activities-${date}.jsonl`
    const fileHandle = await this.dirHandle!.getFileHandle(fileName, { create: true })
    const writable = await fileHandle.createWritable({ keepExisting: true })
    await writable.seek(writable.size) // 追加到末尾
    await writable.write(JSON.stringify(activity) + '\n')
    await writable.close()
  }

  // 读取近 N 天的活动数据
  async readRecent(days: number): Promise<ActivitySummary[]> {
    const activities: ActivitySummary[] = []
    for (let i = 0; i < days; i++) {
      const date = new Date(Date.now() - i * 86400000).toISOString().slice(0, 10)
      const fileName = `activities-${date}.jsonl`
      try {
        const fileHandle = await this.dirHandle!.getFileHandle(fileName)
        const file = await fileHandle.getFile()
        const text = await file.text()
        text.trim().split('\n').forEach(line => {
          if (line) activities.push(JSON.parse(line))
        })
      } catch { /* 文件不存在，跳过 */ }
    }
    return activities
  }

  // 清理过期数据
  async cleanup(keepDays: number): Promise<void> {
    const cutoff = new Date(Date.now() - keepDays * 86400000).toISOString().slice(0, 10)
    for await (const entry of this.dirHandle!.values()) {
      if (entry.name.startsWith('activities-') && entry.name.endsWith('.jsonl')) {
        const date = entry.name.replace('activities-', '').replace('.jsonl', '')
        if (date < cutoff) await this.dirHandle!.removeEntry(entry.name)
      }
    }
  }
}
```

**本地 JSONL 文件格式（每行一条）：**

```jsonl
{"type":"demo_generate","framework":"react","keywords":["表单","验证","组件"],"language":"ts","ts":1714500000}
{"type":"kb_search","keywords":["useEffect","清理"],"resultCount":3,"ts":1714500100}
{"type":"link_click","framework":"react","keywords":["hooks","effect"],"ts":1714500200}
```

每条 ~150-200 字节，只存关键词摘要，不存原始 prompt 全文。

#### 模式 2：云端存储（可选）

用户选择上传到服务器时，使用 `user_activities` 表：

| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PK |
| user_id | UUID | FK -> users(id), NOT NULL |
| action_type | VARCHAR(30) | NOT NULL |
| keywords | TEXT[] | NOT NULL — 提取后的关键词（不存原始 prompt） |
| framework_id | UUID | FK, nullable |
| language | VARCHAR(30) | |
| created_at | TIMESTAMPTZ | NOT NULL |

CREATE INDEX idx_activities_user_time ON user_activities (user_id, created_at DESC);

> **注意：** 云端模式同样只存关键词摘要，不存原始 prompt 全文。每条 ~100-150 字节。

#### 存储估算

| 指标 | 值 |
|---|---|
| 每条记录大小 | ~150-200 字节（本地） / ~100-150 字节（云端） |
| 日均记录数 | ~30 条 |
| 日均存储 | ~5KB/用户 |
| 30 天存储 | ~150KB/用户 |
| 1000 用户（云端） | ~150MB |

#### 存储配置 API

```
GET    /api/user/activity-config          — 获取存储配置
PUT    /api/user/activity-config          — 更新存储配置
POST   /api/user/activity-config/check    — 检查本地目录权限状态
```

**activity_config 数据库表：**

| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PK |
| user_id | UUID | FK -> users(id), UNIQUE |
| storage_mode | VARCHAR(20) | NOT NULL DEFAULT 'local' (local / cloud) |
| local_dir_name | VARCHAR(200) | 用户选择的本地目录名（用于恢复权限） |
| keep_days | INTEGER | DEFAULT 30 (7/14/30/90) |
| created_at | TIMESTAMPTZ | NOT NULL |
| updated_at | TIMESTAMPTZ | NOT NULL |

### skill_suggestions（AI 推荐的候选 Skill）
| Column | Type | Constraints |
|--------|------|-------------|
| id | UUID | PK |
| user_id | UUID | FK -> users(id), NOT NULL |
| name | VARCHAR(200) | NOT NULL |
| description | TEXT | NOT NULL |
| trigger_description | TEXT | NOT NULL |
| category | VARCHAR(50) | |
| suggested_steps | JSONB | NOT NULL — AI 生成的步骤列表 |
| source_summary | TEXT | NOT NULL — 基于哪些活动生成的（供用户参考） |
| source_activity_ids | UUID[] | 关联的活动 ID |
| status | VARCHAR(20) | DEFAULT 'pending' (pending / accepted / dismissed) |
| created_at | TIMESTAMPTZ | NOT NULL |
| updated_at | TIMESTAMPTZ | NOT NULL |

---

## API 设计

### 认证
**注意：** 登录和注册在初始访问时不需要，系统允许匿名浏览公共内容。只有当用户使用特定功能（如配置 AI、生成 Demo、保存 Skill 等）时，才会拦截并提示要求登录。

```
POST   /api/auth/register              — 注册
POST   /api/auth/login                 — 登录，返回 JWT
POST   /api/auth/refresh               — 刷新 token
```

### 用户 AI 配置
```
GET    /api/user/ai-config             — 获取配置（key 脱敏）
PUT    /api/user/ai-config             — 更新配置
POST   /api/user/ai-config/test        — 测试连通性
GET    /api/providers                   — 支持的 provider 列表
```

### 知识模块
```
GET    /api/frameworks                 — 框架列表
GET    /api/frameworks/{slug}/links    — 框架下的文档链接
GET    /api/links/search?q=useEffect   — 全文搜索，返回深链接 URL
```

### Demo 生成（SSE 流式 + ReAct）
```
POST   /api/demos/generate             — 发送 prompt，流式返回代码+解释
       请求体可选 kb_id 字段；不传时由 AI 自主决定是否检索知识库（ReAct 模式）
       SSE events: thought → tool_call → tool_result → code chunks → explanation chunks → done
GET    /api/demos                      — 历史 demo 列表
GET    /api/demos/{id}                 — 单个 demo
DELETE /api/demos/{id}                 — 删除 demo
```

**ReAct 工作流（Demo 生成时）：**

```
用户: "React useEffect 发起 API 请求"
    ↓
AI Thought: 用户需要 React 数据请求示例，先查一下知识库里有没有相关最佳实践
    ↓
AI Action: search_kb(query="useEffect API request", kb_id=xxx)
    ↓
后端执行: 返回知识库中相关代码片段
    ↓
AI Thought: 找到了相关模式，再确认一下 React 官方文档的用法
    ↓
AI Action: search_links(query="useEffect fetch data", framework="react")
    ↓
后端执行: 返回文档深链接
    ↓
AI Final: 综合知识库上下文和文档，生成最终代码 + 解释
```

AI 可用工具：
- `search_kb` — 语义搜索用户的知识库
- `search_links` — 全文搜索框架文档链接
- `get_framework_info` — 获取框架的基本信息和常用模式

### Skills 构建（SSE 流式 + Function Calling）
```
POST   /api/skills/extract             — 自然语言描述 → 结构化工作流
GET    /api/skills                     — 用户的 skills 列表
GET    /api/skills/{id}                — 单个 skill（含步骤）
PUT    /api/skills/{id}                — 编辑 skill
DELETE /api/skills/{id}                — 删除 skill
POST   /api/skills/{id}/export         — 导出为 Claude Code .md 格式
GET    /api/skills/{id}/export/download — 下载 .md 文件
```

### Skills 智能推荐（基于用户行为分析）
```
GET    /api/skills/suggestions         — 获取 AI 推荐的候选 Skills 列表
POST   /api/skills/suggestions/refresh — 手动触发重新分析（默认每天自动运行一次）
PUT    /api/skills/suggestions/{id}    — 编辑候选 Skill（用户可修改名称/描述/步骤）
POST   /api/skills/suggestions/{id}/accept  — 确认采纳 → 转为正式 Skill
POST   /api/skills/suggestions/{id}/dismiss — 忽略推荐
```

**智能推荐工作流（双模式）：**

```
用户日常使用平台（生成 Demo、搜索文档、提取 Skill）
    ↓
记录行为事件（只存关键词摘要，不存原始 prompt）
    ├─ 本地模式：写入本地 JSONL 文件（File System Access API）
    └─ 云端模式：写入 user_activities 表（异步，线程池）
    ↓
触发推荐（每天定时 / 用户手动点击"刷新推荐"）
    ↓
┌─ 本地模式 ─────────────────────────────────────┐
│  前端 LocalActivityStorage.readRecent(7)        │
│  → 前端聚类分析（按 framework + 关键词）          │
│  → 将聚类摘要 POST 到后端                       │
└─────────────────────────────────────────────────┘
┌─ 云端模式 ─────────────────────────────────────┐
│  后端 ActivityAnalysisService                   │
│  → 查询 user_activities 近 7 天                 │
│  → 后端聚类分析                                  │
└─────────────────────────────────────────────────┘
    ↓
调用 AI（Function Calling）→ extract_suggested_skill
  输入：聚类后的活动摘要（关键词 + framework + 频次）
  输出：结构化 Skill（name / description / steps）
    ↓
写入 skill_suggestions 表（status=pending）
    ↓
前端 Skills 页面"推荐"Tab 展示候选
    ↓
用户可以：
  - 查看推荐详情和来源活动摘要
  - 直接编辑名称、描述、步骤
  - 确认采纳 → 转为正式 Skill
  - 忽略推荐（不再重复）
```

**聚类策略：**
- 主维度：framework_id（同框架的操作自然聚集）
- 次维度：prompt 关键词提取（TF-IDF 或简单的名词短语提取）
- 阈值：同一聚类内至少 3 条活动记录
- 时间窗口：默认 7 天，可配置 7 / 14 / 30 天

**示例：**

用户近 7 天的 Demo 生成记录：
| # | prompt | framework | 时间 |
|---|--------|-----------|------|
| 1 | React 表单组件 + 表单验证 | react | 3 天前 |
| 2 | React 受控输入组件 + TypeScript 类型 | react | 3 天前 |
| 3 | React useForm Hook + 错误处理 | react | 2 天前 |
| 4 | React 表单提交 + loading 状态 | react | 1 天前 |
| 5 | React 动态表单字段 | react | 今天 |

→ 聚类结果：5 条记录，framework=react，关键词=[表单, React, 组件, 验证, 输入]
→ AI 推荐 Skill：

```json
{
  "name": "创建 React 表单组件",
  "description": "使用受控组件模式创建带验证、错误处理和提交状态的 React 表单",
  "triggerDescription": "Use when creating a React form component with validation, error handling, and submit logic",
  "category": "frontend",
  "steps": [
    {"title": "定义表单类型", "description": "创建 FormData 和 FormErrors TypeScript 接口", "stepType": "action"},
    {"title": "创建表单组件骨架", "description": "使用 useState 管理表单状态和错误状态", "stepType": "action"},
    {"title": "实现验证逻辑", "description": "编写 validate 函数，返回错误对象", "stepType": "action"},
    {"title": "处理表单提交", "description": "实现 onSubmit，包含 loading 状态和错误处理", "stepType": "action"},
    {"title": "添加表单 UI", "description": "渲染输入框、错误提示、提交按钮", "stepType": "action"}
  ]
}
```

**Function Calling Schema（提取推荐 Skill）：**

```json
{
  "name": "extract_suggested_skill",
  "description": "从用户行为模式中提取推荐的工作流 Skill",
  "parameters": {
    "type": "object",
    "properties": {
      "name": { "type": "string" },
      "description": { "type": "string" },
      "triggerDescription": { "type": "string" },
      "category": { "type": "string", "enum": ["frontend", "backend", "mobile", "devops", "other"] },
      "steps": {
        "type": "array",
        "items": {
          "type": "object",
          "properties": {
            "title": { "type": "string" },
            "description": { "type": "string" },
            "stepType": { "type": "string", "enum": ["action", "decision", "validation", "reference"] }
          },
          "required": ["title", "description", "stepType"]
        }
      },
      "reasoning": { "type": "string", "description": "为什么推荐这个 Skill，基于哪些行为模式" }
    },
    "required": ["name", "description", "triggerDescription", "steps", "reasoning"]
  }
}
```

**Function Calling 保证结构化输出：**

传统方式靠 prompt 约束 AI 输出 JSON，格式不稳定。改用 Function Calling，AI 必须按 schema 输出：

```json
{
  "name": "extract_skill",
  "description": "从用户描述中提取结构化工作流",
  "parameters": {
    "type": "object",
    "properties": {
      "name": { "type": "string", "description": "Skill 名称" },
      "description": { "type": "string", "description": "一句话描述" },
      "triggerDescription": { "type": "string", "description": "触发条件，以 Use when... 开头" },
      "category": { "type": "string", "enum": ["frontend", "backend", "mobile", "devops", "other"] },
      "steps": {
        "type": "array",
        "items": {
          "type": "object",
          "properties": {
            "title": { "type": "string" },
            "description": { "type": "string" },
            "stepType": { "type": "string", "enum": ["action", "decision", "validation", "reference"] },
            "codeTemplate": { "type": "string" },
            "expectedOutput": { "type": "string" },
            "notes": { "type": "string" }
          },
          "required": ["title", "description", "stepType"]
        }
      }
    },
    "required": ["name", "description", "triggerDescription", "steps"]
  }
}
```

AI 返回结构化 JSON → 后端直接反序列化为 `Skill` 对象 → 无需 JSON 解析重试。

**降级方案：** 模型不支持 Function Calling 时，退化为 prompt + 正则提取，解析失败则重试（最多 2 次）。

### 知识库管理
```
POST   /api/kb                         — 创建知识库
GET    /api/kb                         — 用户的知识库列表
GET    /api/kb/{id}                    — 知识库详情（含文档列表）
DELETE /api/kb/{id}                    — 删除知识库（级联删除文档和分块）

POST   /api/kb/{id}/documents          — 上传文档（MD/TXT，自动分块+向量化）
GET    /api/kb/{id}/documents          — 文档列表
DELETE /api/kb/documents/{docId}       — 删除文档

GET    /api/kb/{id}/search?q=xxx       — 语义搜索知识库（返回相关片段）
```

---

## Claude Code Skill 导出格式

```markdown
---
name: create-react-component
description: Use when creating a new React component with TypeScript types and tests
---

# Create React Component

## Overview
Standard workflow for creating a new React component...

## Steps

### Step 1: Create component file
Create the .tsx file with the component skeleton...

### Step 2: Add TypeScript types
Define Props interface and any local types...
...
```

`description` 字段遵循 CSO 原则：以 "Use when..." 开头，只描述触发条件。

---

## 分阶段实施

### Phase 1: 基础框架 + 知识搜索 (MVP)

**后端：**
1. 初始化 Spring Boot 项目（WebFlux, JPA, Security, PostgreSQL, Flyway）
2. Docker Compose 启动 PostgreSQL
3. Flyway 迁移：users, frameworks, knowledge_links
4. 实现 KnowledgeController 全文搜索接口
5. 种子数据：3-5 个框架（React, Spring Boot, Android），20-30 条知识链接
6. JWT 认证（注册/登录）

**前端：**
1. Vite + React + TypeScript + Tailwind 项目初始化
2. 路由：`/` `/knowledge` `/demos` `/skills` `/login` `/settings`
3. Layout（Header + 侧边栏）
4. SearchBar 组件（防抖搜索）
5. FrameworkGrid + LinkCard（点击在新标签页打开深链接）
6. API 客户端 + JWT 拦截器

**交付物：** 搜索 "React useEffect"，看到结果卡片，点击跳转到 react.dev 对应锚点。

### Phase 2: Demo 生成器 + ReAct 引擎

**后端：**
1. `AiProviderAdapter` 接口（含 Function Calling）+ `OpenAiAdapter` / `AnthropicAdapter` 实现
2. `AiFunction`、`AiChunk` 数据模型
3. `ReActAgent` — 多轮推理循环引擎（最多 3 轮工具调用）
4. `user_ai_configs` 表和 CRUD 接口
5. DemoController — `/api/demos/generate` 返回 SSE，注册 `search_kb` / `search_links` / `get_framework_info` 工具
6. Demo 实体 + 仓库
7. 前端设置页 — AI Provider 配置表单

**前端：**
1. useSSE hook（扩展支持 thought / tool_call / tool_result 事件类型）
2. Settings 页面（Provider 选择、API Key 输入、模型选择、连通性测试）
3. DemoGenerator 页面（输入框 + 框架/语言选择 + 生成按钮）
4. StreamingOutput 实时渲染代码（语法高亮）+ ReAct 推理过程可视化
5. CodeViewer（一键复制）

**交付物：** 配置好自己的 AI API 后，输入 "React useEffect 发起 API 请求"，AI 自主搜索知识库和文档，实时看到推理过程和生成的 demo。

### Phase 3: 知识库（RAG）

**后端：**
1. 安装 pgvector 扩展，Flyway 迁移：knowledge_bases, kb_documents, kb_chunks
2. Spring AI 集成：配置 EmbeddingModel（复用用户 AI Provider 配置）
3. 文档上传接口 — 接收 MD/TXT，自动分块（按段落/固定长度）
4. 向量化服务 — 调用用户配置的 AI API 生成 embedding，存入 pgvector
5. RAG 检索服务 — 语义搜索 kb_chunks，返回 top-K 相关片段
6. DemoController 扩展 — 接收可选 kb_id，检索后注入 system prompt

**前端：**
1. KnowledgeBase 页面（知识库列表、创建、删除）
2. KbDetail 页面（文档列表、上传文档、语义搜索测试）
3. DemoGenerator 扩展 — 新增知识库选择下拉框
4. 上传组件（拖拽上传 MD/TXT，显示处理进度）

**交付物：** 上传几篇 React 最佳实践的 MD 文档，创建 demo 时选择该知识库，生成的代码风格与文档一致。

### Phase 4: Skills 构建器 + 智能推荐（Function Calling）

**后端：**
1. 定义 `extract_skill` / `extract_suggested_skill` Function Calling schema
2. SkillController — CRUD + 提取（调用 `streamWithTools`，AI 直接输出结构化 Skill）+ 导出
3. SkillExportService — 生成 Claude Code .md 格式
4. `user_activities` 表 + `ActivityRecordService`（异步写入，独立线程池）
5. `activity_config` 表 + ActivityConfigController（存储模式配置）
6. `skill_suggestions` 表 + SkillSuggestionController（CRUD + accept / dismiss）
7. `ActivityAnalysisService` — 云端模式聚类分析 + 调用 AI 生成推荐 Skill
8. 定时任务：每天凌晨分析近 7 天活动（仅云端模式）
9. 降级方案：模型不支持 Function Calling 时，退化为 prompt + 正则解析 JSON

**前端：**
1. SkillEditor（文本域描述工作流 → AI 提取 → 可视化编辑步骤）
2. SkillStepsList（步骤列表，支持拖拽排序）
3. SkillExportDialog（预览 .md、下载、复制）
4. Skills 列表页 — 分"我的 Skills"和"推荐"两个 Tab
5. SkillSuggestionCard — 展示推荐详情、来源活动摘要、采纳/忽略按钮
6. 采纳前支持编辑：名称、描述、步骤均可修改后再确认
7. `LocalActivityStorage` — File System Access API 封装（本地 JSONL 读写）
8. 本地模式下的前端聚类分析逻辑
9. Settings 页面 — 数据存储位置选择（本地目录 / 云端）

**交付物：** 使用平台一周后，Skills 页面自动出现"推荐"区域，展示基于近期操作模式提取的候选 Skill。用户可查看来源、编辑内容后采纳，或忽略推荐。数据默认存储在用户本地目录，不上传服务器。

### Phase 5: 完善

- 更多框架种子数据（Vue, Angular, Kotlin, Swift, Django 等）
- 分页、加载态、错误处理
- 搜索缓存（Caffeine）
- OpenAPI 文档
- 公共 demo/skill 画廊

---

## 项目目录结构

```
D:\Dev\devknowledge\
├── frontend/
│   ├── package.json
│   ├── vite.config.ts
│   ├── tsconfig.json
│   ├── src/
│   │   ├── main.tsx
│   │   ├── App.tsx
│   │   ├── api/
│   │   │   ├── client.ts              # fetch 封装 + JWT 拦截器
│   │   │   ├── knowledge.ts
│   │   │   ├── demos.ts               # SSE 处理
│   │   │   ├── skills.ts
│   │   │   ├── auth.ts
│   │   │   ├── settings.ts            # AI 配置接口
│   │   │   └── kb.ts                  # 知识库 API
│   │   ├── components/
│   │   │   ├── layout/
│   │   │   │   ├── Header.tsx
│   │   │   │   ├── Sidebar.tsx
│   │   │   │   └── Layout.tsx
│   │   │   ├── knowledge/
│   │   │   │   ├── FrameworkGrid.tsx
│   │   │   │   ├── LinkCard.tsx
│   │   │   │   └── SearchBar.tsx
│   │   │   ├── demos/
│   │   │   │   ├── DemoGenerator.tsx
│   │   │   │   ├── CodeViewer.tsx
│   │   │   │   └── StreamingOutput.tsx
│   │   │   ├── skills/
│   │   │   │   ├── SkillEditor.tsx
│   │   │   │   ├── SkillStepsList.tsx
│   │   │   │   ├── SkillExportDialog.tsx
│   │   │   │   └── SkillSuggestionCard.tsx  # 推荐 Skill 卡片
│   │   │   ├── kb/
│   │   │   │   ├── KbList.tsx
│   │   │   │   ├── KbDetail.tsx
│   │   │   │   ├── DocumentUpload.tsx
│   │   │   │   └── SemanticSearch.tsx
│   │   │   └── settings/
│   │   │       └── AiConfigForm.tsx    # AI Provider 配置表单
│   │   ├── pages/
│   │   │   ├── HomePage.tsx
│   │   │   ├── KnowledgePage.tsx
│   │   │   ├── DemoPage.tsx
│   │   │   ├── SkillsPage.tsx
│   │   │   ├── KbPage.tsx
│   │   │   ├── LoginPage.tsx
│   │   │   └── SettingsPage.tsx
│   │   ├── hooks/
│   │   │   ├── useSSE.ts
│   │   │   └── useAuth.ts
│   │   ├── stores/
│   │   │   └── authStore.ts
│   │   ├── storage/
│   │   │   └── LocalActivityStorage.ts     # File System Access API 封装（本地 JSONL 读写）
│   │   └── types/
│   │       └── api.ts
│   └── index.html
│
├── backend/
│   ├── pom.xml
│   └── src/main/java/com/devknowledge/
│       ├── DevKnowledgeApplication.java
│       ├── config/
│       │   ├── SecurityConfig.java
│       │   ├── CorsConfig.java
│       │   ├── AiProviderConfig.java
│       │   └── AsyncConfig.java               # 活动记录异步线程池
│       ├── controller/
│       │   ├── AuthController.java
│       │   ├── KnowledgeController.java
│       │   ├── DemoController.java
│       │   ├── SkillController.java
│       │   ├── SkillSuggestionController.java  # 推荐 Skill CRUD + accept/dismiss
│       │   ├── ActivityConfigController.java   # 存储模式配置 + 目录权限检查
│       │   ├── SettingsController.java
│       │   └── KbController.java
│       ├── service/
│       │   ├── AuthService.java
│       │   ├── KnowledgeService.java
│       │   ├── DemoService.java
│       │   ├── SkillService.java
│       │   ├── SkillExportService.java
│       │   ├── SkillSuggestionService.java    # 推荐 Skill 管理
│       │   ├── ActivityAnalysisService.java   # 行为聚类分析 + AI 提取推荐
│       │   ├── ActivityRecordService.java     # 活动记录写入
│       │   ├── KbService.java
│       │   ├── EmbeddingService.java
│       │   └── ai/
│       │       ├── AiProviderAdapter.java       # 统一接口（含 Function Calling）
│       │       ├── AiFunction.java              # 工具定义（name + description + schema）
│       │       ├── AiChunk.java                 # AI 输出块（TEXT / TOOL_CALL / DONE）
│       │       ├── OpenAiAdapter.java           # OpenAI 兼容格式
│       │       ├── AnthropicAdapter.java        # Claude 格式
│       │       ├── AiProviderFactory.java       # 根据 provider 类型创建 adapter
│       │       └── ReActAgent.java              # ReAct 循环引擎（tool dispatch + 多轮推理）
│       ├── model/
│       │   ├── User.java
│       │   ├── UserAiConfig.java
│       │   ├── Framework.java
│       │   ├── KnowledgeLink.java
│       │   ├── Demo.java
│       │   ├── Skill.java
│       │   ├── SkillStep.java
│       │   ├── KnowledgeBase.java
│       │   ├── KbDocument.java
│       │   ├── KbChunk.java
│       │   ├── UserActivity.java              # 用户行为事件
│       │   ├── SkillSuggestion.java           # 推荐候选 Skill
│       │   └── ActivityConfig.java            # 用户存储配置（本地/云端）
│       ├── repository/
│       │   ├── UserRepository.java
│       │   ├── UserAiConfigRepository.java
│       │   ├── FrameworkRepository.java
│       │   ├── KnowledgeLinkRepository.java
│       │   ├── DemoRepository.java
│       │   ├── SkillRepository.java
│       │   ├── KnowledgeBaseRepository.java
│       │   ├── KbDocumentRepository.java
│       │   ├── KbChunkRepository.java
│       │   ├── UserActivityRepository.java
│       │   ├── SkillSuggestionRepository.java
│       │   └── ActivityConfigRepository.java
│       ├── dto/
│       │   ├── request/
│       │   │   ├── LoginRequest.java
│       │   │   ├── RegisterRequest.java
│       │   │   ├── GenerateDemoRequest.java
│       │   │   ├── ExtractSkillRequest.java
│       │   │   └── AiConfigRequest.java
│       │   └── response/
│       │       ├── AuthResponse.java
│       │       ├── LinkSearchResult.java
│       │       ├── DemoResponse.java
│       │       ├── SkillResponse.java
│       │       ├── SkillSuggestionResponse.java  # 推荐 Skill 响应（含来源摘要）
│       │       └── AiConfigResponse.java
│       └── security/
│           ├── JwtTokenProvider.java
│           └── JwtAuthenticationFilter.java
│
├── docker-compose.yml     # PostgreSQL (pgvector/pgvector:pg16 镜像)
├── .env.example
└── README.md
```

---

## 关键技术实现

### Maven 依赖新增

```xml
<!-- Spring AI - RAG + Embedding -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
</dependency>

<!-- pgvector -->
<dependency>
    <groupId>com.pgvector</groupId>
    <artifactId>pgvector</artifactId>
    <version>0.1.6</version>
</dependency>
```

### SSE 流式传输（Spring Boot WebFlux）

SSE 事件类型扩展，支持 ReAct 过程可视化：

```java
@GetMapping(value = "/api/demos/generate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<String>> generateDemo(@RequestBody GenerateDemoRequest req) {
    UserAiConfig config = aiConfigRepository.findByUserId(getCurrentUserId());

    // 定义 AI 可用工具
    List<AiFunction> tools = List.of(
        new AiFunction("search_kb", "语义搜索知识库", kbSearchSchema),
        new AiFunction("search_links", "搜索框架文档链接", linkSearchSchema),
        new AiFunction("get_framework_info", "获取框架信息", frameworkInfoSchema)
    );
    Map<String, ToolHandler> handlers = Map.of(
        "search_kb", args -> kbService.semanticSearch(parseKbId(args), parseQuery(args)),
        "search_links", args -> knowledgeService.fullTextSearch(parseQuery(args)),
        "get_framework_info", args -> knowledgeService.getFramework(parseSlug(args))
    );

    return reactAgent.run(systemPrompt, req.getPrompt(), tools, handlers, config)
        .map(chunk -> ServerSentEvent.builder(chunk).build());
}
```

前端 SSE 事件类型：
```
event: thought     → AI 的推理过程（"让我先搜索知识库..."）
event: tool_call   → 工具调用（显示 "正在搜索知识库..."）
event: tool_result → 工具返回结果
event: code        → 代码块
event: explanation → 解释文本
event: done        → 完成
event: error       → 错误
```

### 统一 AI 适配层（支持 Function Calling）
```java
public interface AiProviderAdapter {
    // 普通流式对话
    Flux<String> streamCompletion(String systemPrompt, String userMessage, UserAiConfig config);

    // Function Calling 流式对话
    Flux<AiChunk> streamWithTools(String systemPrompt, String userMessage,
        List<AiFunction> tools, UserAiConfig config);
}

// OpenAiAdapter — 兼容 OpenAI / DeepSeek / 通义千问等（tools 参数）
// AnthropicAdapter — Claude API（tool_use blocks）
// AiProviderFactory — 根据 config.provider 返回对应 adapter
```

### ReAct Agent 引擎

```java
@Service
public class ReActAgent {

    private static final int MAX_ITERATIONS = 3;

    public Flux<String> run(String systemPrompt, String userMessage,
            List<AiFunction> tools, Map<String, ToolHandler> handlers,
            UserAiConfig config) {

        AiProviderAdapter adapter = aiProviderFactory.getAdapter(config.getProvider());
        List<Map<String, String>> conversation = new ArrayList<>();
        conversation.add(Map.of("role", "system", "content", systemPrompt));
        conversation.add(Map.of("role", "user", "content", userMessage));

        return Flux.create(sink -> {
            for (int i = 0; i < MAX_ITERATIONS; i++) {
                List<AiChunk> chunks = adapter.streamWithTools(
                    systemPrompt, userMessage, tools, config
                ).collectList().block();

                boolean hasToolCall = false;
                for (AiChunk chunk : chunks) {
                    if (chunk.type() == AiChunkType.TOOL_CALL) {
                        hasToolCall = true;
                        // 执行工具，将结果加入对话
                        ToolHandler handler = handlers.get(chunk.functionName());
                        String result = handler.apply(chunk.arguments());
                        conversation.add(Map.of("role", "assistant",
                            "content", "Calling " + chunk.functionName()));
                        conversation.add(Map.of("role", "tool", "content", result));
                    } else if (chunk.type() == AiChunkType.TEXT) {
                        sink.next(chunk.content());
                    }
                }
                if (!hasToolCall) break; // AI 输出最终答案，结束循环
            }
            sink.complete();
        });
    }
}

@FunctionalInterface
interface ToolHandler {
    String apply(String arguments);
}
```

Demo 生成时注册工具：
```java
Map<String, ToolHandler> demoTools = Map.of(
    "search_kb", args -> kbService.semanticSearch(parseKbId(args), parseQuery(args)),
    "search_links", args -> knowledgeService.fullTextSearch(parseQuery(args)),
    "get_framework_info", args -> knowledgeService.getFramework(parseSlug(args))
);
```

### PostgreSQL 全文搜索
```sql
ALTER TABLE knowledge_links ADD COLUMN search_vector tsvector
  GENERATED ALWAYS AS (
    to_tsvector('english', coalesce(title,'') || ' ' || coalesce(description,'') || ' ' || array_to_string(tags,' '))
  ) STORED;
CREATE INDEX idx_search ON knowledge_links USING GIN (search_vector);
```

### API Key 加密存储
用户配置的 API Key 使用 AES 加密后存入数据库，前端展示时脱敏（只显示前 4 位 + 后 4 位）。

### 异步活动记录线程池

活动记录不阻塞主流程，使用独立线程池异步写入：

```java
@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean("activityExecutor")
    public Executor activityExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("activity-");
        executor.setRejectedExecutionHandler(new CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}

@Service
public class ActivityRecordService {
    @Async("activityExecutor")
    @Transactional
    public void recordActivity(String actionType, UUID userId,
            String[] keywords, UUID frameworkId, String language) {
        UserActivity activity = new UserActivity();
        activity.setUserId(userId);
        activity.setActionType(actionType);
        activity.setKeywords(keywords);
        activity.setFrameworkId(frameworkId);
        activity.setLanguage(language);
        activity.setCreatedAt(Instant.now());
        activityRepository.save(activity);
    }
}
```

### 知识库 RAG 流程（Spring AI + pgvector）

```java
// 1. 文档分块
@Bean
public TokenTextSplitter textSplitter() {
    return new TokenTextSplitter(200, 20, 5, 10000, true);
}

// 2. Embedding 生成（复用用户 AI Provider 配置）
public List<float[]> generateEmbeddings(List<String> chunks, UserAiConfig config) {
    EmbeddingModel model = embeddingModelFactory.create(config);
    return chunks.stream()
        .map(chunk -> model.embed(chunk))
        .toList();
}

// 3. RAG 检索 — 生成 demo 时注入上下文
public String buildRagPrompt(String userPrompt, UUID kbId, UUID userId) {
    List<KbChunk> relevant = kbChunkRepository.searchByVector(
        kbId, embeddingService.embed(userPrompt), 5
    );
    String context = relevant.stream()
        .map(KbChunk::getContent)
        .collect(Collectors.joining("\n---\n"));
    return "根据以下参考代码风格和模式：\n" + context + "\n\n用户需求：" + userPrompt;
}
```

```sql
-- pgvector 语义搜索
SELECT id, content, 1 - (embedding <=> $1) AS similarity
FROM kb_chunks
WHERE kb_id = $2
ORDER BY embedding <=> $1
LIMIT 5;
```

---

## 风险与应对

| 风险 | 应对 |
|---|---|
| 不同 AI 服务商 API 格式不一致 | 统一 Adapter 接口 + Factory 模式隔离差异 |
| 用户 API Key 泄露风险 | AES 加密存储 + 前端脱敏展示 + HTTPS 传输 |
| AI 响应延迟高 | SSE 流式传输缓解感知延迟，后端 120s 超时 |
| Skill 提取 JSON 格式异常 | Function Calling 保证结构化输出；不支持时退化为 prompt + 正则，最多重试 2 次 |
| 部分模型不支持 Function Calling | 后端检测模型能力，自动降级为纯 prompt 模式；Provider 配置页标注能力标签 |
| ReAct 循环超时或死循环 | 硬性限制最多 3 轮工具调用，每轮单独超时 30s，总计不超过 120s |
| 工具调用结果过长截断 | search_kb / search_links 返回 top-3，每条结果限制 500 字符 |
| 中文全文搜索效果差 | 后续加 pg_trgm 三元组搜索或 jieba 分词 |
| WebFlux + JPA 阻塞 | Schedulers.boundedElastic() 包装，Phase 5 可迁 R2DBC |
| Embedding API 调用成本/限流 | 分块异步处理，失败重试，展示处理进度 |
| pgvector 大规模数据性能 | ivfflat 索引 + 按 kb_id 分区，数据量大时考虑 HNSW 索引 |
| 推荐 Skill 质量不高 | 聚类阈值≥3 条相似行为；用户可编辑后再采纳；忽略后不再重复推荐 |
| 行为数据量不足无法聚类 | 新用户 7 天内不触发推荐；数据不足时静默跳过，不报错 |
| 活动记录影响写入性能 | 异步写入（消息队列或 @Async），不阻塞主流程 |

---

## 验证方式

1. **Phase 1：** 启动前后端，搜索 "useEffect"，确认返回结果且链接可跳转
2. **Phase 2：** 配置 AI Provider 后生成 demo，确认：
   - 流式显示、语法高亮、可复制
   - AI 自主调用 `search_kb` / `search_links` 工具（SSE 中可见 thought / tool_call 事件）
   - ReAct 推理过程在前端可视化展示
3. **Phase 3：** 上传 MD 文档到知识库，语义搜索返回相关片段；生成 demo 时 AI 自动检索知识库，代码风格与文档一致
4. **Phase 4：** 描述工作流提取 skill，确认：
   - Function Calling 直接输出结构化 JSON（无解析重试）
   - 降级测试：使用不支持 Function Calling 的模型，确认退化为 prompt 模式仍可工作
   - 步骤可编辑，导出 .md 符合 Claude Code 格式
5. **Phase 4 智能推荐：** 使用平台一周后，确认：
   - Skills 页面"推荐"Tab 出现基于近期行为的候选 Skill
   - 推荐内容可编辑（名称、描述、步骤）
   - 采纳后转为正式 Skill，忽略后不再重复推荐
   - 来源活动摘要可查看，推荐理由清晰
6. 将导出的 .md 放入 `~/.claude/skills/` 后在 Claude Code 中用 `/skill-name` 验证可调用
