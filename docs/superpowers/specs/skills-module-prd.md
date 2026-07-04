# Skills 模块增强 — 产品需求文档 (PRD)

> **版本**: 1.0
> **日期**: 2026-06-09
> **状态**: Draft
> **负责人**: Product Requirements Manager

---

## 1. 背景与目标

### 1.1 现状分析

DevKnowledge 平台的 Skills 模块目前处于**前端已完成、后端缺失**的半成品状态：

- **前端 SkillsPage** 已实现三个核心功能区：Skill 提取（SSE 流式）、Skill 管理（查看/导出）、AI 推荐（采纳/编辑/忽略）
- **前端 API 客户端** (`skills.ts`) 已定义完整的 RESTful 接口调用，包括 `/skills/*` 和 `/skills/suggestions/*` 两组端点
- **后端无任何实现** — 无 Controller、无 Service、无 Mapper、无数据库表，所有 API 调用均返回 404

行为数据存储方面：

- **LocalActivityStorage** 使用 File System Access API + IndexedDB 实现本地 JSONL 存储，但存在两个关键问题：
  1. 仅支持 Chromium 内核浏览器（Chrome/Edge），Firefox/Safari 不可用
  2. `recordActivity()` 函数已定义但**未在任何地方调用**，行为数据完全未采集
- **StorageSettings** 云端同步选项为占位实现，切换无实际效果

### 1.2 建设目标

1. **打通 Skills 全链路** — 实现后端 CRUD + AI 提取 + 智能推荐，使前端已有功能可用
2. **建立行为数据采集** — 接入 `recordActivity()`，为推荐引擎提供数据基础
3. **存储方案增强** — 服务端存储为主，本地存储为辅，实现混合存储策略
4. **补全前端交互** — Skill 在线编辑、搜索过滤、分类管理

---

## 2. 用户场景

### 场景一：从工作流描述提取 Skill

> 作为开发者，我希望描述一段工作流程（如"创建 React 组件需要类型定义、单元测试、Storybook 故事"），系统能自动提取出结构化的 Skill，包含有序步骤、类型标注和代码模板，以便我复用和分享。

**当前状态**: 前端输入框和 SSE 流式展示已就绪，后端提取服务缺失。

### 场景二：管理已保存的 Skills

> 作为开发者，我希望查看、编辑、删除已保存的 Skills，按分类和关键词快速检索，并导出为 Markdown 文件归档。

**当前状态**: 前端列表展示和导出按钮已就绪，后端 CRUD 缺失；编辑功能仅在推荐卡片上存在，保存的 Skill 无法编辑。

### 场景三：基于使用习惯获得 Skill 推荐

> 作为开发者，我希望系统能分析我的使用模式（频繁搜索某个框架文档、反复生成类似 Demo），自动推荐可复用的 Skill 工作流，我可以选择采纳、编辑后采纳或忽略。

**当前状态**: 前端推荐卡片（采纳/编辑/忽略）已就绪，但无行为数据采集、无推荐引擎后端。

### 场景四：跨设备同步行为数据

> 作为开发者，我在公司电脑和家里的电脑上都使用 DevKnowledge，我希望行为数据能同步到云端，在任一设备上都能获得一致的推荐体验。

**当前状态**: 本地存储仅限 Chromium 浏览器，云端同步为占位实现。

### 场景五：导出 Skill 为可执行文档

> 作为团队 Tech Lead，我希望将团队最佳实践提取为 Skill 并导出为 Markdown，包含步骤说明、代码模板和预期输出，便于新人 onboarding。

**当前状态**: 前端导出按钮和 Blob 下载已就绪，后端导出逻辑（Markdown 生成）缺失。

---

## 3. 功能需求

### 3.1 Skill CRUD

#### 3.1.1 创建

- **来源**: AI 提取自动创建（主路径），或用户手动创建（低优先级）
- **字段**: name, description, category, frameworkId, triggerDescription, steps[], isPublic, version
- **约束**: 每个 Skill 必须至少包含 1 个 Step；name 不可为空

#### 3.1.2 查询

- **列表查询**: 支持分页，按 updatedAt 降序
- **详情查询**: 返回完整 Skill 含所有 Steps
- **搜索**: 关键词匹配 name + description，支持 category 和 frameworkId 过滤

#### 3.1.3 更新

- **在线编辑**: 支持修改 name、description、category、triggerDescription
- **步骤编辑**: 支持增删改 Steps，自动维护 stepOrder
- **版本控制**: 每次更新 version + 1

#### 3.1.4 删除

- **软删除**: 标记删除而非物理删除，保留数据用于分析
- **级联**: 删除 Skill 时级联删除关联的 Steps

#### 3.1.5 导出

- **Markdown 导出**: 生成结构化 Markdown 文档，包含：
  - 标题（Skill name）
  - 描述（description）
  - 触发条件（triggerDescription）
  - 步骤列表（含 stepType 标签、title、description、codeTemplate、expectedOutput）
- **文件下载**: 返回 `{ content: string }` 供前端生成 Blob 下载

### 3.2 Skill 提取（AI-Powered, SSE 流式）

#### 3.2.1 输入

```typescript
interface ExtractSkillRequest {
  description: string       // 用户描述的工作流（必填）
  frameworkId?: string      // 关联框架（可选）
  category?: string         // 分类标签（可选）
}
```

#### 3.2.2 处理流程

1. 接收用户描述文本
2. 构造 System Prompt，指示 AI 从描述中提取结构化 Skill
3. AI 返回 JSON 格式的 Skill 结构（name, description, triggerDescription, steps[]）
4. **SSE 流式输出**提取过程，前端实时展示
5. 提取完成后自动保存到数据库（已登录用户）

#### 3.2.3 System Prompt 设计要点

- 指示 AI 输出合法 JSON，schema 与前端 `Skill` 类型一致
- steps 中 stepType 限定为 `action | decision | validation | reference`
- 为每个 step 生成合理的 codeTemplate 和 expectedOutput（如适用）
- 输出包含 `[DONE]` 信号标记流式结束

#### 3.2.4 匿名与登录用户差异

| 能力 | 匿名用户 | 登录用户 |
|------|---------|---------|
| 提取 Skill | 支持（SSE 展示结果） | 支持 |
| 自动保存 | 不保存 | 自动保存到数据库 |
| 查看历史 | 不支持 | 支持 |

### 3.3 Skill 推荐引擎

#### 3.3.1 数据输入

- **行为数据**: 用户的 demo_generate、kb_search、link_click、skill_extract、skill_export 活动记录
- **已有 Skills**: 用户已保存的 Skills（避免重复推荐）
- **框架偏好**: 用户最常使用的 framework 统计

#### 3.3.2 推荐策略

**Phase 1 — 基于规则的推荐**（P0）:

1. **频率分析**: 统计近 30 天内用户行为中出现频率最高的 framework + keyword 组合
2. **模式识别**: 如果用户对同一类操作（如"创建 React 组件"）重复执行 3 次以上，触发推荐
3. **模板匹配**: 维护一组通用 Skill 模板（如"CRUD API 开发"、"组件开发"、"数据库迁移"），与用户行为模式匹配

**Phase 2 — AI 增强推荐**（P1）:

1. 将用户近期行为摘要作为上下文，调用 LLM 生成个性化 Skill 推荐
2. 推荐结果包含 sourceSummary，说明推荐依据（如"你近 7 天搜索了 12 次 React Router 相关文档"）

#### 3.3.3 推荐生命周期

```
生成(pending) → 用户查看 → 采纳(accepted) / 编辑后采纳 / 忽略(dismissed)
```

- **pending**: 默认状态，展示在"推荐"Tab
- **accepted**: 转为正式 Skill，从推荐列表移除
- **dismissed**: 标记忽略，不再展示

#### 3.3.4 刷新机制

- 用户点击"刷新推荐"按钮时触发
- 后端重新分析行为数据，生成新的推荐
- 已 dismissed 的推荐不重复生成

### 3.4 行为数据采集

#### 3.4.1 采集点

| 行为类型 | 触发位置 | 采集数据 |
|---------|---------|---------|
| `demo_generate` | DemoPage 生成完成时 | framework, keywords[], language |
| `kb_search` | KbPage 搜索时 | keywords[], resultCount |
| `link_click` | KnowledgePage 点击链接时 | framework, keywords[] |
| `skill_extract` | SkillsPage 提取完成时 | keywords[] |
| `skill_export` | SkillsPage 导出时 | keywords[] |

#### 3.4.2 采集策略

- **服务端采集优先**: 行为数据随 API 请求一并记录到服务端数据库
- **本地采集为辅**: 保留 LocalActivityStorage 作为离线降级方案
- **去重**: 同一行为 5 分钟内不重复记录

### 3.5 存储方案增强

#### 3.5.1 存储模式

| 模式 | 说明 | 适用场景 |
|------|------|---------|
| **服务端存储**（默认） | 行为数据存储在 PostgreSQL，通过 API 访问 | 登录用户，跨设备同步 |
| **本地存储** | 使用 File System Access API，数据存本地文件 | 离线使用，隐私优先 |
| **混合模式** | 本地写入 + 异步同步到服务端 | 网络不稳定环境 |

#### 3.5.2 StorageSettings 增强

- **存储模式切换**: 实际持久化用户选择（当前仅为 UI 状态）
- **云端同步状态**: 显示最后同步时间、同步数据量
- **数据导出**: 支持将本地 JSONL 数据一键上传到服务端
- **浏览器兼容提示**: 非 Chromium 浏览器自动降级为服务端存储，显示友好提示

### 3.6 前端增强

#### 3.6.1 Skill 在线编辑

- 已保存的 Skill 支持内联编辑（复用 SkillSuggestionCard 的编辑模式）
- 步骤支持拖拽排序
- 编辑后自动保存，version + 1

#### 3.6.2 搜索与过滤

- 列表页顶部增加搜索框，实时过滤 name + description
- 分类标签过滤（category chips）
- 框架过滤（frameworkId 下拉）

#### 3.6.3 分类管理

- 预设分类：`frontend`、`backend`、`devops`、`database`、`testing`、`other`
- 支持自定义分类输入
- 分类用于列表过滤和推荐引擎

---

## 4. 数据模型

### 4.1 Skills 主表

```sql
-- V14__create_skills_tables.sql

-- Skills 表
CREATE TABLE skills (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name                VARCHAR(200) NOT NULL,
    description         TEXT,
    category            VARCHAR(50),
    framework_id        UUID REFERENCES frameworks(id),
    trigger_description TEXT,
    exported_content    TEXT,
    version             INTEGER NOT NULL DEFAULT 1,
    is_public           BOOLEAN NOT NULL DEFAULT false,
    is_deleted          BOOLEAN NOT NULL DEFAULT false,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_skills_user_id ON skills(user_id);
CREATE INDEX idx_skills_user_category ON skills(user_id, category);
CREATE INDEX idx_skills_user_updated ON skills(user_id, updated_at DESC);
```

### 4.2 Skill Steps 表

```sql
-- Skill 步骤表
CREATE TABLE skill_steps (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    skill_id        UUID NOT NULL REFERENCES skills(id) ON DELETE CASCADE,
    step_order      INTEGER NOT NULL,
    title           VARCHAR(300) NOT NULL,
    description     TEXT,
    step_type       VARCHAR(20) NOT NULL DEFAULT 'action',  -- action|decision|validation|reference
    code_template   TEXT,
    expected_output TEXT,
    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_skill_steps_skill_id ON skill_steps(skill_id, step_order);
```

### 4.3 Skill Suggestions 表

```sql
-- Skill 推荐表
CREATE TABLE skill_suggestions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name                VARCHAR(200) NOT NULL,
    description         TEXT,
    trigger_description TEXT,
    category            VARCHAR(50),
    suggested_steps     JSONB NOT NULL DEFAULT '[]',  -- 结构化步骤 JSON
    source_summary      TEXT,                          -- 推荐依据说明
    status              VARCHAR(20) NOT NULL DEFAULT 'pending',  -- pending|accepted|dismissed
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_skill_suggestions_user ON skill_suggestions(user_id, status);
```

### 4.4 用户行为表

```sql
-- 用户行为记录表
CREATE TABLE user_activities (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type        VARCHAR(30) NOT NULL,  -- demo_generate|kb_search|link_click|skill_extract|skill_export
    framework   VARCHAR(50),
    keywords    TEXT[] DEFAULT '{}',
    language    VARCHAR(50),
    result_count INTEGER,
    metadata    JSONB,                 -- 扩展字段，存储额外上下文
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_user_activities_user_time ON user_activities(user_id, created_at DESC);
CREATE INDEX idx_user_activities_type ON user_activities(user_id, type);
```

---

## 5. API 设计

### 5.1 Skills API

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| `POST` | `/api/skills/extract` | SSE 流式提取 Skill | 可选 |
| `GET` | `/api/skills` | 获取当前用户 Skill 列表 | 必须 |
| `GET` | `/api/skills/{id}` | 获取 Skill 详情（含 Steps） | 必须 |
| `PUT` | `/api/skills/{id}` | 更新 Skill（含 Steps） | 必须 |
| `DELETE` | `/api/skills/{id}` | 删除 Skill（软删除） | 必须 |
| `POST` | `/api/skills/{id}/export` | 导出 Skill 为 Markdown | 必须 |
| `GET` | `/api/skills/{id}/export/download` | 下载 Markdown 文件 | 必须(token) |

#### 5.1.1 提取请求

```json
POST /api/skills/extract
Content-Type: application/json

{
  "description": "创建 React 组件，包含 TypeScript 类型定义、单元测试和 Storybook 故事",
  "frameworkId": "optional-uuid",
  "category": "frontend"
}
```

**响应**: SSE 流，事件类型 `text` 携带提取过程文本，`done` 标记结束。提取完成后自动保存（登录用户）。

#### 5.1.2 Skill 列表

```json
GET /api/skills?category=frontend&keyword=react&page=0&size=20

Response 200:
[
  {
    "id": "uuid",
    "name": "React 组件开发",
    "description": "...",
    "category": "frontend",
    "frameworkId": "uuid",
    "triggerDescription": "...",
    "version": 1,
    "isPublic": false,
    "steps": [...],
    "createdAt": "2026-06-09T10:00:00Z",
    "updatedAt": "2026-06-09T10:00:00Z"
  }
]
```

#### 5.1.3 更新请求

```json
PUT /api/skills/{id}
Content-Type: application/json

{
  "name": "更新后的名称",
  "description": "更新后的描述",
  "steps": [
    {
      "id": "existing-step-uuid",   // 有 id = 更新
      "stepOrder": 1,
      "title": "...",
      "description": "...",
      "stepType": "action"
    },
    {
      "stepOrder": 2,               // 无 id = 新增
      "title": "新步骤",
      "description": "...",
      "stepType": "validation"
    }
  ]
}
```

### 5.2 Suggestions API

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| `GET` | `/api/skills/suggestions` | 获取推荐列表 | 必须 |
| `POST` | `/api/skills/suggestions/refresh` | 刷新推荐 | 必须 |
| `PUT` | `/api/skills/suggestions/{id}` | 编辑推荐（采纳前） | 必须 |
| `POST` | `/api/skills/suggestions/{id}/accept` | 采纳推荐 → 转为 Skill | 必须 |
| `POST` | `/api/skills/suggestions/{id}/dismiss` | 忽略推荐 | 必须 |

### 5.3 Activities API

| 方法 | 路径 | 说明 | 认证 |
|------|------|------|------|
| `POST` | `/api/activities` | 记录用户行为 | 必须 |
| `GET` | `/api/activities` | 查询行为记录（分页） | 必须 |
| `DELETE` | `/api/activities/cleanup` | 清理过期数据 | 必须 |

#### 5.3.1 记录行为

```json
POST /api/activities
Content-Type: application/json

{
  "type": "kb_search",
  "framework": "react",
  "keywords": ["hooks", "useState"],
  "resultCount": 15
}
```

---

## 6. 非功能需求

### 6.1 性能

| 指标 | 目标 |
|------|------|
| Skill 列表查询 | < 200ms (p95) |
| Skill 提取 SSE 首字节 | < 2s |
| 推荐生成（含 AI） | < 10s |
| 行为记录写入 | < 50ms（异步，不阻塞主流程） |

### 6.2 存储

| 数据类型 | 保留策略 | 预估容量 |
|---------|---------|---------|
| Skills | 永久（软删除后 90 天物理清理） | 单用户 < 1000 条 |
| Suggestions | dismissed 后 30 天清理 | 单用户 < 100 条 |
| Activities | 默认保留 90 天，用户可配置 | 单用户 < 10000 条/月 |

### 6.3 浏览器兼容性

| 功能 | Chrome | Edge | Firefox | Safari |
|------|--------|------|---------|--------|
| Skill 提取 (SSE) | 支持 | 支持 | 支持 | 支持 |
| Skill 管理 | 支持 | 支持 | 支持 | 支持 |
| 本地存储 (File System Access API) | 支持 | 支持 | **不支持** | **不支持** |
| 服务端存储 | 支持 | 支持 | 支持 | 支持 |

**降级策略**: 非 Chromium 浏览器自动切换为服务端存储，StorageSettings 显示兼容性提示。

### 6.4 安全

- 行为数据仅限登录用户访问，API 强制校验 JWT
- 行为数据不含敏感内容（仅 keywords + metadata 摘要）
- Skill 的 isPublic 字段控制可见性：public Skill 可被其他用户查看

---

## 7. 验收标准

### 7.1 P0 验收（核心功能）

- [ ] 用户输入工作流描述，点击"提取 Skill"后 SSE 流式显示提取过程，最终展示结构化 Skill
- [ ] 已登录用户的提取结果自动保存，刷新页面后在"我的 Skills"列表可见
- [ ] 点击 Skill 列表项可查看详情（含所有 Steps）
- [ ] 点击"导出 .md"可下载包含完整信息的 Markdown 文件
- [ ] 点击"刷新推荐"后，基于用户行为数据生成推荐（至少有规则引擎）
- [ ] 推荐卡片的"采纳"操作将推荐转为正式 Skill
- [ ] 推荐卡片的"忽略"操作将推荐从列表移除
- [ ] `recordActivity()` 在 demo_generate、kb_search 等关键路径被调用

### 7.2 P1 验收（体验增强）

- [ ] 已保存的 Skill 支持在线编辑（name、description、steps）
- [ ] Skill 列表支持关键词搜索和分类过滤
- [ ] 推荐卡片支持编辑后采纳
- [ ] 推荐引擎包含 AI 增强推荐（基于 LLM 的个性化推荐）
- [ ] StorageSettings 中云端同步选项实际生效，行为数据写入服务端
- [ ] 非 Chromium 浏览器自动降级为服务端存储，显示友好提示

### 7.3 P2 验收（高级功能）

- [ ] Skill 步骤支持拖拽排序
- [ ] 用户可自定义分类标签
- [ ] 本地 JSONL 数据一键迁移到服务端
- [ ] StorageSettings 显示同步状态（最后同步时间、数据量）
- [ ] public Skills 可被其他用户查看和 fork

---

## 8. 优先级排序

### P0 — 核心链路（必须交付）

| # | 功能 | 说明 |
|---|------|------|
| 1 | 数据库迁移 | Skills、Steps、Suggestions、Activities 四张表 |
| 2 | Skill CRUD 后端 | Controller + Service + Mapper，完整增删改查 |
| 3 | Skill 提取服务 | AI 提取 + SSE 流式输出，复用现有 ReAct Agent |
| 4 | Skill 导出 | Markdown 生成 + 文件下载 |
| 5 | 行为数据采集 | 在关键路径接入 recordActivity，写入服务端 |
| 6 | 推荐引擎（规则版） | 基于频率分析和模式匹配的推荐生成 |
| 7 | 推荐 CRUD | Suggestions 的查询、采纳、忽略 |

### P1 — 体验增强

| # | 功能 | 说明 |
|---|------|------|
| 8 | Skill 在线编辑 | 前端编辑 UI + 后端更新 API |
| 9 | 搜索与过滤 | 关键词搜索 + 分类/框架过滤 |
| 10 | AI 增强推荐 | LLM 驱动的个性化推荐 |
| 11 | 服务端存储打通 | StorageSettings 实际切换 + 数据同步 |
| 12 | 浏览器兼容降级 | 非 Chromium 自动切换服务端存储 |

### P2 — 高级功能

| # | 功能 | 说明 |
|---|------|------|
| 13 | 拖拽排序 | Skill Steps 拖拽重排 |
| 14 | 自定义分类 | 用户自定义分类标签管理 |
| 15 | 数据迁移工具 | 本地 JSONL → 服务端一键迁移 |
| 16 | 同步状态面板 | StorageSettings 展示同步详情 |
| 17 | Skill 社区 | public Skills 共享与 fork |

---

## 附录 A：前后端接口对照

前端 `skills.ts` 已定义的 API 调用与后端需实现的对应关系：

| 前端调用 | HTTP 方法 | 后端端点 | 状态 |
|---------|----------|---------|------|
| `skillsApi.extract()` | POST (SSE) | `/api/skills/extract` | 待实现 |
| `skillsApi.getSkills()` | GET | `/api/skills` | 待实现 |
| `skillsApi.getSkill(id)` | GET | `/api/skills/{id}` | 待实现 |
| `skillsApi.updateSkill(id, data)` | PUT | `/api/skills/{id}` | 待实现 |
| `skillsApi.deleteSkill(id)` | DELETE | `/api/skills/{id}` | 待实现 |
| `skillsApi.exportSkill(id)` | POST | `/api/skills/{id}/export` | 待实现 |
| `skillsApi.downloadSkill(id)` | GET | `/api/skills/{id}/export/download` | 待实现 |
| `skillsApi.getSuggestions()` | GET | `/api/skills/suggestions` | 待实现 |
| `skillsApi.refreshSuggestions()` | POST | `/api/skills/suggestions/refresh` | 待实现 |
| `skillsApi.updateSuggestion(id, data)` | PUT | `/api/skills/suggestions/{id}` | 待实现 |
| `skillsApi.acceptSuggestion(id)` | POST | `/api/skills/suggestions/{id}/accept` | 待实现 |
| `skillsApi.dismissSuggestion(id)` | POST | `/api/skills/suggestions/{id}/dismiss` | 待实现 |

## 附录 B：与现有代码的集成点

| 组件 | 文件 | 集成方式 |
|------|------|---------|
| ReAct Agent | `backend/.../ai/ReActAgent.java` | Skill 提取复用 Agent 引擎，自定义 System Prompt |
| AiConfigService | `backend/.../AiConfigService.java` | 获取用户激活的 AI 配置用于提取 |
| JwtTokenProvider | `backend/.../security/JwtTokenProvider.java` | Controller 层用户身份验证 |
| MyBatis Plus | 全局 | Mapper 继承 BaseMapper，Service 层用 Schedulers.boundedElastic() 包装 |
| useSSE Hook | `frontend/.../hooks/useSSE.ts` | 提取页面复用现有 SSE 流式 Hook |
| api.stream() | `frontend/.../api/client.ts` | SSE 请求复用现有 stream 方法 |
| SkillSuggestionCard | `frontend/.../skills/SkillSuggestionCard.tsx` | 推荐卡片 UI 已完成，对接后端 API |
