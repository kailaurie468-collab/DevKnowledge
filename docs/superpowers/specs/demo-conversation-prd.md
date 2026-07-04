# Demo 对话化功能 PRD

> **文档版本**: v1.0
> **创建日期**: 2026-06-09
> **状态**: Draft

---

## 1. 背景与目标

### 1.1 现状问题

当前 Demo 生成是一次性交互模式：用户输入 prompt，系统通过 ReAct Agent 生成代码，输出完成后对话结束。存在以下痛点：

- **无法追问**：用户想在生成结果基础上修改（如"加上错误处理"、"改成 TypeScript"），必须重新描述完整需求
- **上下文丢失**：每次生成独立运行，AI 不知道上一轮生成了什么，用户需要反复粘贴之前的代码
- **迭代效率低**：调试阶段需要反复调整，每次都要从头构建 prompt，浪费时间和 token
- **历史不可回溯**：只能查看历史 Demo 的静态快照，无法回到某个对话继续迭代

### 1.2 目标

将 Demo 页面从"单次生成"升级为"多轮对话"模式：

| 维度 | 现状 | 目标 |
|------|------|------|
| 交互模式 | 单轮，每次独立 | 多轮对话，带上下文 |
| 上下文 | 无记忆 | AI 记住本轮对话中所有生成内容 |
| 迭代方式 | 重新输入完整 prompt | 自然语言追问和修改 |
| 历史管理 | 扁平 Demo 列表 | 会话列表，每个会话含多轮对话 |
| RAG/Wiki | 每次单独配置 | 会话级别配置，自动继承 |

### 1.3 核心价值

- **降低迭代成本**：用户只需说"加上日志"而非重新描述整个需求
- **提升生成质量**：AI 有上下文，能做出更精准的修改
- **保留完整轨迹**：对话历史即开发过程记录，可回溯、可复用

---

## 2. 用户场景

### 场景 1：逐步完善代码

**用户**: "用 React 写一个 TodoList 组件"
**AI**: *(生成 TodoList 代码)*
**用户**: "加上 localStorage 持久化"
**AI**: *(基于上一轮代码修改，只添加持久化逻辑)*
**用户**: "再加一个筛选功能，可以按完成状态筛选"
**AI**: *(在已有代码基础上添加筛选)*

**价值**：用户像和同事对话一样逐步完善代码，无需每次重写完整需求。

### 场景 2：切换技术方案

**用户**: "用 Express 写一个 REST API"
**AI**: *(生成 Express 代码)*
**用户**: "改成 Koa 框架"
**AI**: *(保持相同的业务逻辑，改用 Koa 语法重写)*

**价值**：快速对比不同技术方案，AI 理解"改成"是指同一需求换个框架。

### 场景 3：调试和修复

**用户**: "写一个 React 表单组件"
**AI**: *(生成代码)*
**用户**: "提交时报错 'Cannot read property of undefined'，帮我看看"
**AI**: *(分析上一轮代码中的潜在问题，给出修复)*
**用户**: "加上表单验证"
**AI**: *(在修复后的代码上添加验证逻辑)*

**价值**：调试过程自然流畅，AI 能关联之前的代码分析问题。

### 场景 4：基于知识库迭代

用户在第一个消息中选择了知识库，后续追问自动继承 RAG 检索配置，无需每轮重新选择。

**用户**: *(选择知识库 +)* "根据项目规范写一个用户注册接口"
**AI**: *(检索知识库 + 生成代码)*
**用户**: "加上邮箱验证"
**AI**: *(自动检索知识库中的邮箱验证相关文档 + 修改代码)*

### 场景 5：中断后恢复

用户中途关闭页面，第二天打开 Demo 页面，左侧会话列表显示之前的对话，点击即可恢复上下文继续迭代。

---

## 3. 功能需求

### 3.1 会话管理

#### 3.1.1 创建会话

- 用户首次在 Demo 页面输入 prompt 并点击发送时，自动创建新会话
- 会话标题由 AI 根据第一条消息自动生成（复用现有 `generateTitle` 逻辑，截取前 30 字符）
- 用户也可以手动重命名会话标题
- 每个会话绑定创建时的 `language`、`frameworkId`、`kbId`、`topK`、`retrievalSource` 配置

#### 3.1.2 会话列表

- 左侧面板展示用户的会话列表，按 `updated_at` 倒序排列
- 每个会话项显示：标题、最后一条消息的预览（截取前 50 字符）、最后更新时间
- 支持会话搜索（按标题模糊匹配）
- 支持删除会话（级联删除所有消息）
- 支持重命名会话

#### 3.1.3 会话恢复

- 用户点击会话列表中的某条记录，加载该会话的完整消息历史
- 恢复会话时同时恢复关联的 `language`、`frameworkId`、`kbId` 等配置
- 会话恢复后，用户可以直接在该上下文中继续输入

#### 3.1.4 新建对话

- 顶部提供"新建对话"按钮，清空当前界面，回到初始状态
- 新建对话时，language/framework/kb 配置重置为默认值

### 3.2 消息模型

#### 3.2.1 消息类型

每条消息包含以下字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | UUID | 消息唯一标识 |
| `session_id` | UUID | 所属会话 |
| `role` | enum | `user` / `assistant` / `system` |
| `content` | TEXT | 消息内容 |
| `content_type` | enum | `text` / `code` / `mixed`（混合了文本和代码） |
| `metadata` | JSONB | 扩展字段（见下方） |
| `created_at` | TIMESTAMPTZ | 创建时间 |

#### 3.2.2 消息元数据（metadata）

`metadata` 字段存储结构化信息，用于前端渲染和上下文管理：

```json
// 用户消息
{
  "type": "user_message"
}

// AI 回复（包含代码生成）
{
  "type": "ai_response",
  "code_blocks": [
    { "lang": "typescript", "code": "..." }
  ],
  "tool_calls": [
    { "name": "search_links", "args": "...", "result": "..." }
  ],
  "tokens_used": 1500,
  "retrieval_chunks": 3,
  "model_version": "gpt-4o"
}

// 系统消息（如会话创建、配置变更通知）
{
  "type": "system_notice",
  "notice": "knowledge_base_changed"
}
```

#### 3.2.3 与现有 Demo 表的关系

- 每个会话中的 AI 回复（包含代码块）仍会生成 `demos` 记录，保持与现有 Token 统计、RAG 指标的兼容
- `demos` 表新增 `session_id` 和 `message_id` 外键，关联到对话化后的新表
- 现有 Demo 列表页面继续保留，展示所有生成记录（包括对话中的和旧版独立生成的）

### 3.3 上下文管理

#### 3.3.1 上下文构建策略

每次用户发送新消息时，系统需要将对话历史组装为 AI 的 messages 数组。策略如下：

1. **系统提示词**（固定）：语言、框架、工具使用规则（复用现有 `buildSystemPrompt`）
2. **RAG 上下文**（条件注入）：如果会话绑定了知识库，对用户最新消息执行 RAG 检索，将结果注入系统提示词
3. **对话历史**（动态裁剪）：将历史消息按时间顺序传入 messages

#### 3.3.2 上下文窗口管理

- **Token 预算**：对话历史占用的 token 不超过模型 maxTokens 的 60%（剩余留给系统提示词 + RAG + 当前轮生成）
- **滑动窗口**：当历史消息总 token 超出预算时，从最早的消息开始裁剪，但保留最近 N 轮（N 默认 6）
- **代码压缩**：超过 500 字符的代码块在传入上下文时截断为摘要（保留前 200 字符 + "...[已截断]"），减少 token 消耗
- **摘要注入**：被裁剪的历史消息以摘要形式注入系统提示词（如"用户在第 1-3 轮讨论了 React TodoList 组件的实现"）

#### 3.3.3 ReAct Agent 适配

当前 ReAct Agent 的 `run()` 方法接收单条 `userMessage`。对话化改造需要：

- 新增 `runWithHistory()` 方法，接收 `List<ChatMessage>` 替代单条 `userMessage`
- Agent 内部的 messages 列表从传入的历史消息初始化，而非从空列表开始
- 工具调用结果仍然追加到 Agent 内部 messages（与现有逻辑一致）
- 每轮生成完成后，将完整的 assistant 回复（含工具调用轨迹）保存为一条消息

### 3.4 对话式交互

#### 3.4.1 追问与修改

- 用户在对话框中输入自然语言追问，如"加上错误处理"、"改成 async/await 写法"
- AI 基于上下文理解"上一轮代码"的含义，直接输出修改后的完整代码或增量 diff
- 追问时自动继承会话的 language/framework/kb 配置，用户无需重新选择

#### 3.4.2 重新生成

- 每条 AI 回复旁提供"重新生成"按钮
- 重新生成时，移除该轮及之后的所有消息，用相同的用户 prompt 重新运行
- 重新生成时保留原始的 RAG 检索配置

#### 3.4.3 分支对话（P2）

- 用户可以从某条历史消息处"分叉"，创建新的对话分支
- 分支共享分叉点之前的历史，之后独立发展
- 实现方式：复制会话 + 截断到分叉点

### 3.5 与 RAG/Wiki 检索的集成

#### 3.5.1 会话级配置

- 知识库选择（`kbId`、`topK`、`retrievalSource`）在会话创建时确定，后续消息自动继承
- 用户可以在对话过程中切换知识库配置（通过设置面板），切换后后续消息使用新配置
- 配置变更时插入系统消息记录（如"已切换知识库：项目文档 -> API 规范"）

#### 3.5.2 自动 RAG 检索

- 每轮用户消息发送时，如果会话绑定了知识库，自动执行 RAG 预检索
- 检索 query 使用用户的最新消息文本
- 检索结果注入系统提示词（与现有逻辑一致）
- RAG 指标（相似度、chunk 数量等）记录在消息的 metadata 中

#### 3.5.3 工具调用

- ReAct Agent 的工具集（search_links、get_framework_info、search_kb）保持不变
- 工具调用的次数和结果记录在对应 AI 消息的 metadata 中
- 工具调用可视化（思考过程、工具调用事件）在对话界面中以内联方式展示

### 3.6 SSE 流式输出

#### 3.6.1 对话模式下的 SSE 行为

- 每轮对话生成仍使用 SSE 流式输出（复用现有 `useSSE` hook）
- SSE 事件类型不变：`thought` / `tool_call` / `text` / `done` / `error`
- 流式输出实时追加到当前对话的最后一条 AI 消息气泡中
- 流式过程中，输入框禁用，显示"生成中..."状态

#### 3.6.2 并发控制

- 同一时间只允许一个生成任务运行
- 流式过程中用户点击"停止"按钮，中断当前生成（AbortController）
- 中断后，已生成的部分内容保留为一条不完整的 AI 消息（metadata 标记 `interrupted: true`）

---

## 4. 交互设计

### 4.1 页面布局

采用**左侧会话列表 + 右侧对话区**的双栏布局：

```
+------------------+----------------------------------------+
|  会话列表 (280px) |  对话区 (flex-1)                         |
|                  |                                        |
|  [新建对话]       |  [语言] [框架] [知识库]  (会话配置栏)      |
|                  |                                        |
|  会话 1 (active)  |  +----------------------------------+  |
|  会话 2           |  | 用户消息气泡                      |  |
|  会话 3           |  +----------------------------------+  |
|  ...             |  | AI 回复气泡（含代码块）             |  |
|                  |  | [复制] [重新生成]                  |  |
|                  |  +----------------------------------+  |
|  [搜索会话...]    |  | 用户消息气泡                      |  |
|                  |  +----------------------------------+  |
|                  |  | AI 回复气泡                       |  |
|                  |  +----------------------------------+  |
|                  |                                        |
|                  |  [输入框                          ] [发送]|
+------------------+----------------------------------------+
```

### 4.2 对话区设计

#### 4.2.1 配置栏

- 位于对话区顶部，显示当前会话的语言、框架、知识库配置
- 点击配置项可展开编辑面板修改（修改后对后续消息生效）
- 新建会话时，配置栏可编辑；恢复历史会话时，显示该会话的配置

#### 4.2.2 消息列表

- 用户消息：右对齐气泡，浅色背景
- AI 回复：左对齐气泡，白色背景
- AI 回复中的代码块：使用现有 `CodeBlock` 组件渲染（带语言标签 + 复制按钮）
- AI 回复中的文本：使用现有 `MarkdownOutput` 组件渲染
- ReAct 推理过程（思考、工具调用）：折叠展示，默认收起，点击展开查看详情

#### 4.2.3 消息操作

每条 AI 回复提供以下操作按钮：
- **复制**：复制整个回复内容到剪贴板
- **复制代码**：仅复制代码块内容
- **重新生成**：删除该轮及之后的消息，重新生成

#### 4.2.4 输入区

- 位于对话区底部，固定定位
- 多行文本框，支持 Shift+Enter 换行，Enter 发送
- 发送按钮：点击或 Enter 触发生成
- 停止按钮：流式生成时替换发送按钮，点击中断生成
- placeholder 文本随上下文变化：
  - 新会话："描述你想要的代码..."
  - 已有对话："继续输入，如 '加上错误处理' 或 '改成 TypeScript'..."

### 4.3 会话列表

#### 4.3.1 会话项

每个会话项显示：
- 会话标题（AI 自动生成或用户重命名）
- 最后一条消息预览（截取前 50 字符）
- 最后更新时间（相对时间，如"3 分钟前"、"昨天"）
- 语言标签（小徽章，如 `TS`、`Python`）

#### 4.3.2 会话操作

- 点击：加载会话
- 右键/长按：弹出菜单（重命名、删除）
- 悬停：显示删除图标

#### 4.3.3 会话搜索

- 顶部搜索框，按标题模糊匹配
- 实时过滤（输入即搜索）

### 4.4 代码块交互

- **复制按钮**：一键复制代码到剪贴板
- **语言标签**：显示代码语言，点击可切换语法高亮（P2）
- **展开/折叠**：超过 20 行的代码块默认折叠，显示"展开全部"按钮
- **Diff 视图（P2）**：当 AI 回复是修改上一轮代码时，提供"查看 diff"按钮，高亮显示变更部分

---

## 5. 数据模型

### 5.1 新增表

#### 5.1.1 `demo_sessions` 表

```sql
CREATE TABLE demo_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(300) NOT NULL DEFAULT '新对话',
    language VARCHAR(50),
    framework_id UUID REFERENCES frameworks(id),
    kb_id UUID REFERENCES knowledge_bases(id),
    top_k INTEGER DEFAULT 3,
    retrieval_source VARCHAR(20) DEFAULT 'none',  -- 'rag' | 'wiki' | 'none'
    message_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_demo_sessions_user_id ON demo_sessions (user_id);
CREATE INDEX idx_demo_sessions_updated_at ON demo_sessions (updated_at DESC);
```

#### 5.1.2 `demo_messages` 表

```sql
CREATE TABLE demo_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES demo_sessions(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL,  -- 'user' | 'assistant' | 'system'
    content TEXT NOT NULL,
    content_type VARCHAR(20) NOT NULL DEFAULT 'text',  -- 'text' | 'code' | 'mixed'
    metadata JSONB,  -- 扩展字段（工具调用、token 使用等）
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_demo_messages_session_id ON demo_messages (session_id, created_at);
```

### 5.2 现有表变更

#### 5.2.1 `demos` 表新增字段

```sql
ALTER TABLE demos ADD COLUMN session_id UUID REFERENCES demo_sessions(id) ON DELETE SET NULL;
ALTER TABLE demos ADD COLUMN message_id UUID REFERENCES demo_messages(id) ON DELETE SET NULL;

CREATE INDEX idx_demos_session_id ON demos (session_id);
```

### 5.3 ER 关系

```
users (1) ──< (N) demo_sessions (1) ──< (N) demo_messages
                                     |
                                     └──< (N) demos (通过 session_id 关联)

demo_sessions >── (1) frameworks (可选)
demo_sessions >── (1) knowledge_bases (可选)
```

---

## 6. 非功能需求

### 6.1 性能

| 指标 | 目标 |
|------|------|
| 会话列表加载 | < 200ms（分页加载，每页 20 条） |
| 历史消息加载 | < 300ms（单会话所有消息） |
| 首条消息响应 | 与现有 Demo 生成一致（SSE 首字节 < 2s） |
| 追问响应 | 与首条消息一致（上下文组装不引入额外延迟） |

### 6.2 存储

- 单个会话最大消息数：100 条（超出后提示用户新建会话）
- 单条消息最大长度：50,000 字符
- 会话保留期限：永久（用户手动删除）
- metadata JSONB 字段最大：10,000 字符

### 6.3 Token 限制

- 对话历史 token 预算：模型 maxTokens 的 60%
- 单轮生成最大 token：与现有 Demo 生成一致（由用户 AI 配置的 maxTokens 控制）
- 上下文压缩策略：超出预算时裁剪早期消息，保留最近 6 轮

### 6.4 安全

- 会话数据严格按用户隔离，API 校验 `session.user_id == current_user.id`
- 会话删除为级联删除，清除所有关联消息和 Demo 记录的外键引用
- metadata 中不存储 API Key 等敏感信息

---

## 7. 验收标准

### 7.1 会话管理

- [ ] 用户首次输入 prompt 时自动创建会话，左侧列表出现新会话项
- [ ] 点击历史会话可恢复对话，消息列表完整显示
- [ ] 会话列表按最后更新时间倒序排列
- [ ] 支持会话搜索、重命名、删除
- [ ] "新建对话"按钮清空界面，回到初始状态

### 7.2 多轮对话

- [ ] 用户在已有对话中输入追问，AI 能引用之前的代码上下文
- [ ] 示例：先生成 TodoList，追问"加上 localStorage"，AI 在原代码基础上修改
- [ ] 示例：先用 Express 生成 API，追问"改成 Koa"，AI 保持业务逻辑换框架重写
- [ ] 每轮对话保存为独立的 message 记录

### 7.3 上下文管理

- [ ] 对话历史超出 token 预算时自动裁剪早期消息
- [ ] 裁剪后 AI 仍能理解对话主线（通过摘要注入）
- [ ] 超长代码块在上下文中被截断，不影响当轮生成质量

### 7.4 RAG/Wiki 集成

- [ ] 会话绑定知识库后，后续每轮自动执行 RAG 检索
- [ ] 用户可在对话过程中切换知识库配置
- [ ] RAG 指标正确记录在消息 metadata 中

### 7.5 SSE 流式输出

- [ ] 对话模式下 SSE 流式输出正常工作，实时追加到 AI 消息气泡
- [ ] ReAct 推理过程（思考、工具调用）以内联折叠方式展示
- [ ] 用户可中断生成，已生成内容保留

### 7.6 UI/UX

- [ ] 左侧会话列表 + 右侧对话区双栏布局
- [ ] 代码块带复制按钮，超长代码可折叠
- [ ] AI 回复带"重新生成"按钮
- [ ] 输入框 Enter 发送，Shift+Enter 换行
- [ ] 深色模式适配

### 7.7 兼容性

- [ ] 现有 Demo 列表页面功能不受影响
- [ ] 旧版独立生成的 Demo 仍可正常查看和删除
- [ ] Token 统计和 RAG 指标页面正常工作

---

## 8. 优先级排序

### P0 — 核心功能（MVP）

| 功能 | 说明 |
|------|------|
| 会话创建 | 首次输入自动创建会话 |
| 多轮对话 | 支持追问和修改，AI 保持上下文 |
| 消息持久化 | 会话和消息存储到数据库 |
| SSE 流式输出 | 对话模式下的流式生成 |
| 会话列表 | 展示、切换、删除会话 |
| 上下文管理 | 滑动窗口 + token 预算裁剪 |
| RAG 集成 | 会话级知识库配置，自动检索 |

### P1 — 增强体验

| 功能 | 说明 |
|------|------|
| 重新生成 | 删除当前轮及之后消息，重新生成 |
| 会话搜索 | 按标题模糊搜索历史会话 |
| 配置修改 | 对话过程中切换语言/框架/知识库 |
| 代码折叠 | 超长代码块默认折叠 |
| 中断生成 | 停止按钮中断流式生成 |

### P2 — 高级功能

| 功能 | 说明 |
|------|------|
| 分支对话 | 从历史消息处创建对话分支 |
| Diff 视图 | 高亮显示代码修改的变更部分 |
| 会话导出 | 导出对话为 Markdown 文件 |
| 会话分享 | 生成分享链接（只读） |

---

## 附录 A：API 设计概览

### 新增接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/demo-sessions` | 创建会话 |
| GET | `/api/demo-sessions` | 获取会话列表（分页） |
| GET | `/api/demo-sessions/{id}` | 获取会话详情（含消息） |
| PATCH | `/api/demo-sessions/{id}` | 更新会话（标题、配置） |
| DELETE | `/api/demo-sessions/{id}` | 删除会话（级联删除消息） |
| POST | `/api/demo-sessions/{id}/messages` | 发送消息并生成回复（SSE） |
| POST | `/api/demo-sessions/{id}/messages/{msgId}/regenerate` | 重新生成某条回复 |

### 接口详情

#### POST `/api/demo-sessions/{id}/messages`（SSE）

请求体：
```json
{
  "content": "加上错误处理",
  "retrievalSource": "rag"  // 可选，覆盖会话默认配置
}
```

SSE 事件流（与现有 Demo 生成一致）：
```
event: thought
data: 用户希望在之前的代码基础上添加错误处理...

event: tool_call
data: search_links:{"query":"React error handling best practices"}

event: text
data: 好的，我在之前的 TodoList 基础上添加了错误处理...

event: done
data: [DONE]
```

---

## 附录 B：ReAct Agent 改造要点

### 现有接口

```java
// 当前：单条消息
public Flux<AiChunk> run(String systemPrompt, String userMessage,
                          List<AiFunction> tools, Map<String, ToolHandler> handlers,
                          UserAiConfig config, int maxIterations,
                          Map<String, AtomicInteger> toolCallCounts)
```

### 新增接口

```java
// 新增：带历史消息
public Flux<AiChunk> runWithHistory(String systemPrompt,
                                     List<ChatMessage> history,
                                     List<AiFunction> tools,
                                     Map<String, ToolHandler> handlers,
                                     UserAiConfig config, int maxIterations,
                                     Map<String, AtomicInteger> toolCallCounts)
```

### 改造要点

1. `runWithHistory()` 接收完整的历史消息列表，直接初始化 Agent 的 messages
2. 历史消息中的 `assistant` 角色消息包含 AI 之前的回复（含工具调用轨迹）
3. 工具调用结果仍追加到 Agent 内部 messages（不改变现有逻辑）
4. 生成完成后，将新产生的 assistant 回复保存到 `demo_messages` 表

---

## 附录 C：上下文压缩算法

```
输入: history[] (历史消息列表), tokenBudget (token 预算)
输出: compressed[] (压缩后的消息列表), summary (被裁剪部分的摘要)

1. 计算 history 总 token 数 totalTokens
2. 如果 totalTokens <= tokenBudget，返回 history 原样
3. 从最早的消息开始，逐条累加 token，直到超出预算
4. 将超出部分的消息标记为 "待裁剪"
5. 对 "待裁剪" 消息生成摘要：
   - 提取每条消息的前 100 字符
   - 拼接为 "早期对话摘要：用户讨论了 X，AI 生成了 Y..."
6. 将摘要作为 system 消息插入压缩后的消息列表头部
7. 保留最近 6 轮消息（12 条：6 user + 6 assistant）
8. 返回压缩后的消息列表和摘要
```
