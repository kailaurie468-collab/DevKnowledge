# Demo 对话化架构设计

> **文档版本**: v1.0
> **创建日期**: 2026-06-09
> **状态**: Draft
> **基于 PRD**: `docs/superpowers/specs/demo-conversation-prd.md`

---

## 1. 系统架构概览

### 1.1 组件变更总览

```
┌─ 前端 (React + TypeScript) ─────────────────────────────────────────────┐
│                                                                          │
│  ┌─ 新增 ─────────────────────────────────────────────────────────────┐  │
│  │  ConversationPage.tsx     主页面（会话列表 + 对话区）                │  │
│  │  components/conversation/                                         │  │
│  │    ├── SessionList.tsx     左侧会话列表面板                         │  │
│  │    ├── ChatInput.tsx       底部输入区（多行 + 发送/停止）            │  │
│  │    ├── MessageBubble.tsx   消息气泡（用户/AI）                      │  │
│  │    ├── ConversationView.tsx 消息列表 + 滚动管理                     │  │
│  │    └── SessionConfigBar.tsx 会话配置栏（语言/框架/KB）              │  │
│  │  api/conversations.ts     会话 + 消息 API 客户端                    │  │
│  │  stores/conversationStore.ts Zustand 状态管理                       │  │
│  └────────────────────────────────────────────────────────────────────┘  │
│                                                                          │
│  ┌─ 修改 ─────────────────────────────────────────────────────────────┐  │
│  │  App.tsx                  新增 /conversations 路由                  │  │
│  │  Sidebar.tsx              导航菜单新增"对话 Demo"入口               │  │
│  │  types/api.ts             新增会话/消息类型定义                     │  │
│  │  hooks/useSSE.ts          小幅调整（支持会话模式回调）               │  │
│  └────────────────────────────────────────────────────────────────────┘  │
│                                                                          │
│  ┌─ 保留不变 ─────────────────────────────────────────────────────────┐  │
│  │  DemoPage.tsx             旧版单次生成页面，继续保留                 │  │
│  │  api/demos.ts             旧版 API 客户端                           │  │
│  │  hooks/useSSE.ts          核心逻辑不变                              │  │
│  └────────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────────┘

         │  HTTP + SSE        │  HTTP REST
         ▼                    ▼

┌─ 后端 (Spring Boot WebFlux) ─────────────────────────────────────────────┐
│                                                                          │
│  ┌─ 新增 ─────────────────────────────────────────────────────────────┐  │
│  │  controller/DemoSessionController.java   会话 CRUD + 消息发送      │  │
│  │  service/DemoSessionService.java         会话管理                   │  │
│  │  service/DemoMessageService.java         消息 CRUD + 持久化         │  │
│  │  service/ContextManager.java             上下文窗口管理 + 压缩       │  │
│  │  model/DemoSession.java                  会话实体                   │  │
│  │  model/DemoMessage.java                  消息实体                   │  │
│  │  mapper/DemoSessionMapper.java           会话 Mapper               │  │
│  │  mapper/DemoMessageMapper.java           消息 Mapper               │  │
│  │  dto/DemoSessionRequest.java             会话请求 DTO              │  │
│  │  dto/DemoSessionResponse.java            会话响应 DTO              │  │
│  │  dto/SendMessageRequest.java             发送消息请求 DTO           │  │
│  │  migration/V14__create_demo_sessions.sql  新表迁移                  │  │
│  └────────────────────────────────────────────────────────────────────┘  │
│                                                                          │
│  ┌─ 修改 ─────────────────────────────────────────────────────────────┐  │
│  │  service/ai/ReActAgent.java    新增 runWithHistory() 方法           │  │
│  │  service/DemoService.java      新增 generateDemoWithHistory()      │  │
│  │  model/Demo.java               新增 sessionId/messageId 字段       │  │
│  │  migration/V15__alter_demos_add_session.sql                         │  │
│  └────────────────────────────────────────────────────────────────────┘  │
│                                                                          │
│  ┌─ 保留不变 ─────────────────────────────────────────────────────────┐  │
│  │  controller/DemoController.java    旧版生成接口继续保留             │  │
│  │  service/DemoToolProvider.java     工具定义不变                     │  │
│  │  service/ai/AiProviderAdapter.java 适配器接口不变                   │  │
│  └────────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────────┘
```

### 1.2 关键架构决策

| 决策 | 选择 | 理由 |
|------|------|------|
| 新页面 vs 修改 DemoPage | **新建 ConversationPage** | PRD 要求保留旧版 DemoPage 不受影响；新页面独立演进，避免 DemoPage 过于臃肿 |
| ReActAgent 改造方式 | **新增 runWithHistory() 而非修改 run()** | 向后兼容，旧版 DemoController 调用 run() 不受影响 |
| 上下文管理位置 | **独立 ContextManager 服务** | 职责单一，可复用，便于测试和后续优化 |
| Token 计数策略 | **字符估算 (char * 1.5)** | 与现有 estimateTokens() 一致，无外部依赖；后续可替换为 tiktoken |
| 工具调用轨迹存储 | **JSONB metadata 字段** | 灵活扩展，PostgreSQL JSONB 支持索引查询 |

---

## 2. 后端架构

### 2.1 新增服务

#### 2.1.1 DemoSessionService — 会话管理

```java
@Service
public class DemoSessionService {
    // 依赖: DemoSessionMapper, DemoMessageMapper, DemoMapper

    /** 创建新会话（首次消息时自动创建） */
    Mono<DemoSession> createSession(UUID userId, CreateSessionRequest req);

    /** 获取用户会话列表（分页，按 updated_at 倒序） */
    Mono<Page<DemoSession>> listSessions(UUID userId, int page, int size, String keyword);

    /** 获取会话详情（含最近 N 条消息） */
    Mono<DemoSessionDetail> getSession(UUID userId, UUID sessionId);

    /** 更新会话（标题、配置） */
    Mono<DemoSession> updateSession(UUID userId, UUID sessionId, UpdateSessionRequest req);

    /** 删除会话（级联删除消息 + demos 外键置空） */
    Mono<Void> deleteSession(UUID userId, UUID sessionId);
}
```

**关键实现细节：**
- `createSession` 在第一条消息发送时由 `sendMessage` 内部调用，而非独立 API
- `listSessions` 返回最后一条消息的预览（前 50 字符），通过子查询实现
- `deleteSession` 利用 `ON DELETE CASCADE` 级联删除消息，`ON DELETE SET NULL` 处理 demos 外键

#### 2.1.2 DemoMessageService — 消息管理

```java
@Service
public class DemoMessageService {
    // 依赖: DemoMessageMapper, DemoSessionMapper, ContextManager

    /** 保存用户消息 */
    Mono<DemoMessage> saveUserMessage(UUID sessionId, String content);

    /** 保存 AI 回复（含 metadata：工具调用、token 使用等） */
    Mono<DemoMessage> saveAssistantMessage(UUID sessionId, String content,
                                            String contentType, Map<String, Object> metadata);

    /** 获取会话的完整消息历史 */
    Flux<DemoMessage> getSessionMessages(UUID sessionId);

    /** 获取会话最近 N 轮消息（用于上下文构建） */
    Flux<DemoMessage> getRecentMessages(UUID sessionId, int maxRounds);

    /** 删除某条消息及之后的所有消息（重新生成用） */
    Mono<Void> deleteFromMessage(UUID sessionId, UUID messageId);
}
```

#### 2.1.3 ContextManager — 上下文窗口管理

```java
@Service
public class ContextManager {

    /** 构建对话上下文（含滑动窗口 + 压缩） */
    List<ChatMessage> buildContext(List<DemoMessage> history, String systemPrompt, int maxTokens);

    /** 估算文本 token 数（复用 char * 1.5 策略） */
    int estimateTokens(String text);

    /** 截断超长代码块（> 500 字符 → 前 200 + "...[已截断]"） */
    String truncateCodeBlocks(String content);

    /** 生成被裁剪消息的摘要 */
    String summarizePrunedMessages(List<DemoMessage> pruned);
}
```

**上下文构建算法：**

```
输入: history[] (消息列表), systemPrompt, maxTokens
输出: messages[] (传给 ReActAgent 的 ChatMessage 列表)

1. 计算 tokenBudget = maxTokens * 0.6
2. 计算 history 总 token 数 totalTokens
3. 如果 totalTokens <= tokenBudget:
     直接转换 history → ChatMessage 列表，返回
4. 保留最近 6 轮（12 条）消息，标记为 "保留区"
5. 从保留区之前的最早消息开始，逐条累加 token
6. 当累加超出 tokenBudget 时，停止；之前的标记为 "裁剪区"
7. 对 "裁剪区" 生成摘要：
   - 提取每条消息前 100 字符
   - 拼接为 "早期对话摘要：用户讨论了 X，AI 生成了 Y..."
8. 构建最终 messages:
   [systemPrompt + RAG context] + [摘要消息] + [保留区消息]
9. 对保留区中超长代码块执行截断
```

### 2.2 修改的现有服务

#### 2.2.1 ReActAgent — 新增 runWithHistory()

```java
// 现有方法（不修改）:
public Flux<AiChunk> run(String systemPrompt, String userMessage, ...)

// 新增方法:
public Flux<AiChunk> runWithHistory(
    String systemPrompt,
    List<AiProviderAdapter.ChatMessage> history,  // 完整的对话历史（已由 ContextManager 处理）
    List<AiFunction> tools,
    Map<String, ToolHandler> handlers,
    UserAiConfig config,
    int maxIterations,
    Map<String, AtomicInteger> toolCallCounts)
```

**实现要点：**
- `runWithHistory()` 与 `run()` 的核心区别仅在于 messages 列表的初始化方式
- `run()`: `messages = [new ChatMessage("user", userMessage)]`
- `runWithHistory()`: `messages = new ArrayList<>(history)`
- 后续的 `runRound()` 递归逻辑完全复用，无需修改
- 历史消息中的 assistant 消息可能包含工具调用的文本描述，这对 AI 理解上下文有帮助

**代码变更量极小**：只需在 ReActAgent 中新增一个重载方法，核心推理循环不变。

#### 2.2.2 DemoService — 新增对话式生成方法

```java
/** 对话式生成（带历史上下文） */
public Flux<ServerSentEvent<String>> generateDemoWithHistory(
    UUID userId, UUID sessionId, String userMessage,
    DemoSession session, List<DemoMessage> history)
```

**与现有 `generateDemo()` 的对比：**

| 步骤 | generateDemo() | generateDemoWithHistory() |
|------|---------------|---------------------------|
| 加载 AI 配置 | 相同 | 相同 |
| 构建系统提示词 | 从 request 参数构建 | 从 session 配置构建 |
| RAG 预检索 | 从 request.kbId | 从 session.kbId，每轮用最新消息 query |
| 上下文构建 | 单条 userMessage | ContextManager.buildContext(history) |
| ReAct 调用 | reactAgent.run() | reactAgent.runWithHistory() |
| 输出保存 | 保存到 demos 表 | 保存到 demo_messages 表 + demos 表 |
| 完成后操作 | 无 | 更新 session.updated_at + message_count |

### 2.3 数据模型

#### 2.3.1 DemoSession 实体

```java
@Data
@TableName("demo_sessions")
public class DemoSession {
    @TableId(type = IdType.INPUT)
    private UUID id;
    private UUID userId;
    private String title;            // 默认 "新对话"
    private String language;
    private UUID frameworkId;
    private UUID kbId;
    private Integer topK;            // 默认 3
    private String retrievalSource;  // 'rag' | 'wiki' | 'none'
    private Integer messageCount;    // 默认 0
    private Instant createdAt;
    private Instant updatedAt;
}
```

#### 2.3.2 DemoMessage 实体

```java
@Data
@TableName("demo_messages")
public class DemoMessage {
    @TableId(type = IdType.INPUT)
    private UUID id;
    private UUID sessionId;
    private String role;          // 'user' | 'assistant' | 'system'
    private String content;
    private String contentType;   // 'text' | 'code' | 'mixed'
    @TableField(typeHandler = JsonTypeHandler.class)
    private Map<String, Object> metadata;  // JSONB
    private Instant createdAt;
}
```

**metadata 结构示例：**

```json
// 用户消息
{ "type": "user_message" }

// AI 回复
{
  "type": "ai_response",
  "code_blocks": [{ "lang": "typescript", "code": "..." }],
  "tool_calls": [
    { "name": "search_links", "args": "...", "result": "..." }
  ],
  "tokens_used": 1500,
  "retrieval_chunks": 3,
  "model_version": "gpt-4o",
  "react_rounds": 3
}
```

#### 2.3.3 Demo 表变更

```sql
-- V15: 新增 session_id 和 message_id 外键
ALTER TABLE demos ADD COLUMN session_id UUID REFERENCES demo_sessions(id) ON DELETE SET NULL;
ALTER TABLE demos ADD COLUMN message_id UUID REFERENCES demo_messages(id) ON DELETE SET NULL;
CREATE INDEX idx_demos_session_id ON demos (session_id);
```

#### 2.3.4 需要新增的 TypeHandler

```java
// JSONB ↔ Map<String, Object> 转换
@MappedTypes(Map.class)
public class JsonTypeHandler extends BaseTypeHandler<Map<String, Object>> {
    // 使用 Jackson ObjectMapper 序列化/反序列化
}
```

### 2.4 API 端点

#### DemoSessionController

```java
@RestController
@RequestMapping("/api/demo-sessions")
public class DemoSessionController {

    /** 获取会话列表（分页） */
    @GetMapping
    Mono<Page<DemoSessionResponse>> listSessions(...)

    /** 获取会话详情（含消息） */
    @GetMapping("/{id}")
    Mono<DemoSessionDetailResponse> getSession(...)

    /** 更新会话（标题、配置） */
    @PatchMapping("/{id}")
    Mono<DemoSessionResponse> updateSession(...)

    /** 删除会话 */
    @DeleteMapping("/{id}")
    Mono<Void> deleteSession(...)

    /** 发送消息并获取 AI 回复（SSE 流式） */
    @PostMapping(value = "/{id}/messages", produces = TEXT_EVENT_STREAM_VALUE)
    Flux<ServerSentEvent<String>> sendMessage(...)

    /** 重新生成某条回复（SSE 流式） */
    @PostMapping(value = "/{id}/messages/{msgId}/regenerate", produces = TEXT_EVENT_STREAM_VALUE)
    Flux<ServerSentEvent<String>> regenerateMessage(...)
}
```

**sendMessage 流程：**

```
1. 校验 session 存在 + 属于当前用户
2. 保存用户消息到 demo_messages
3. 加载会话消息历史
4. ContextManager 构建上下文
5. 如果 session.kbId != null，执行 RAG 预检索
6. 调用 reactAgent.runWithHistory()
7. 流式返回 SSE 事件
8. doOnComplete: 保存 AI 回复到 demo_messages + demos 表
9. 更新 session.updated_at + message_count
```

---

## 3. 前端架构

### 3.1 新增页面

#### ConversationPage.tsx — 对话主页

采用 PRD 定义的双栏布局：左侧会话列表 (280px) + 右侧对话区 (flex-1)。

```
ConversationPage
├── SessionList (左侧面板)
│   ├── 新建对话按钮
│   ├── 搜索框
│   └── SessionItem[] (会话列表项)
│       ├── 标题 + 语言标签
│       ├── 最后消息预览
│       └── 相对时间
│
└── 对话区 (右侧面板)
    ├── SessionConfigBar (顶部配置栏)
    │   ├── 语言选择
    │   ├── 框架选择
    │   └── 知识库配置
    │
    ├── ConversationView (消息列表，flex-1, overflow-auto)
    │   └── MessageBubble[]
    │       ├── UserMessageBubble
    │       │   └── 文本内容
    │       └── AssistantMessageBubble
    │           ├── ReActTrace (折叠: 思考 + 工具调用)
    │           ├── MarkdownOutput (复用现有组件)
    │           └── 操作按钮 (复制 / 复制代码 / 重新生成)
    │
    └── ChatInput (底部固定)
        ├── 多行 textarea (Enter 发送, Shift+Enter 换行)
        └── 发送/停止按钮
```

**路由：** `/conversations` (新增) 与 `/demos` (保留) 并存

### 3.2 新增组件

#### 3.2.1 MessageBubble.tsx

```tsx
// 核心职责：根据 role 渲染不同样式的消息气泡
interface MessageBubbleProps {
  message: DemoMessage
  isStreaming?: boolean          // 是否正在流式生成中
  onRegenerate?: () => void      // 重新生成回调
}

// 复用现有组件:
// - MarkdownOutput: 渲染 AI 回复的 markdown 内容
// - CodeBlock: 渲染代码块（带复制按钮）
```

**ReAct 推理过程展示：** AI 消息中的 `metadata.tool_calls` 和 SSE 流式事件中的 `thought`/`tool_call` 事件以内联折叠方式展示。默认收起，点击展开查看详情。

#### 3.2.2 ChatInput.tsx

```tsx
interface ChatInputProps {
  onSend: (message: string) => void
  onStop: () => void
  isStreaming: boolean
  disabled?: boolean
  placeholder?: string
}

// Enter 发送, Shift+Enter 换行
// 流式中：显示停止按钮，输入框禁用
// placeholder 随上下文变化
```

#### 3.2.3 SessionList.tsx

```tsx
interface SessionListProps {
  sessions: DemoSession[]
  activeSessionId: string | null
  onSelect: (id: string) => void
  onNew: () => void
  onDelete: (id: string) => void
  onRename: (id: string, title: string) => void
  onSearch: (keyword: string) => void
}
```

#### 3.2.4 ConversationView.tsx

```tsx
// 核心职责：消息列表渲染 + 自动滚动
interface ConversationViewProps {
  messages: DemoMessage[]
  streamingOutput: string        // 当前流式输出的文本
  streamingEvents: SSEChunk[]    // 当前流式事件（用于 ReAct 可视化）
  isStreaming: boolean
  onRegenerate: (messageId: string) => void
}

// 自动滚动：新消息或流式输出时滚动到底部
// 复用: MarkdownOutput, CodeBlock 组件
```

#### 3.2.5 SessionConfigBar.tsx

```tsx
interface SessionConfigBarProps {
  session: DemoSession | null
  onUpdateConfig: (config: Partial<DemoSession>) => void
  isNew: boolean                 // 新建会话时可编辑，恢复时显示
}

// 显示/编辑: language, frameworkId, kbId, topK, retrievalSource
// 配置变更时插入系统消息通知
```

### 3.3 状态管理 — conversationStore.ts

```typescript
interface ConversationState {
  // 会话列表
  sessions: DemoSession[]
  sessionsLoading: boolean
  sessionsTotal: number

  // 当前会话
  activeSession: DemoSession | null
  messages: DemoMessage[]

  // 流式状态
  isStreaming: boolean
  streamingOutput: string
  streamingEvents: SSEChunk[]

  // Actions
  loadSessions: (page?: number, keyword?: string) => Promise<void>
  createSession: () => void                          // 新建空白会话
  selectSession: (id: string) => Promise<void>       // 加载会话 + 消息
  deleteSession: (id: string) => Promise<void>
  renameSession: (id: string, title: string) => Promise<void>
  updateSessionConfig: (config: Partial<DemoSession>) => void
  sendMessage: (content: string) => Promise<void>    // 发送消息（SSE）
  stopGeneration: () => void                         // 中断生成
  regenerateMessage: (messageId: string) => Promise<void>
  clearCurrent: () => void                           // 回到初始状态
}
```

**关键设计：** 使用 Zustand 的 `immer` 中间件处理不可变更新，`devtools` 中间件便于调试。

### 3.4 API 客户端 — api/conversations.ts

```typescript
export const conversationsApi = {
  // 会话 CRUD
  list: (params?: { page?: number; size?: number; keyword?: string }) =>
    api.get<PageResponse<DemoSession>>('/demo-sessions', params as Record<string, string>),

  get: (id: string) =>
    api.get<DemoSessionDetail>(`/demo-sessions/${id}`),

  update: (id: string, data: Partial<DemoSession>) =>
    api.patch<DemoSession>(`/demo-sessions/${id}`, data),

  delete: (id: string) =>
    api.delete<void>(`/demo-sessions/${id}`),

  // 消息发送（SSE 流式）
  sendMessage: (sessionId: string, content: string, retrievalSource?: string) =>
    (signal: AbortSignal) =>
      api.stream(`/demo-sessions/${sessionId}/messages`, { content, retrievalSource }, signal),

  // 重新生成（SSE 流式）
  regenerate: (sessionId: string, messageId: string) =>
    (signal: AbortSignal) =>
      api.stream(`/demo-sessions/${sessionId}/messages/${messageId}/regenerate`, {}, signal),
}
```

### 3.5 SSE 流式处理

**复用现有 `useSSE` hook**，不做核心逻辑修改。对话模式下的差异通过 `onChunk` 回调处理：

```tsx
// ConversationPage 中的使用方式
const { isStreaming, output, events, stream, cancel, reset } = useSSE()

const handleSend = async (content: string) => {
  // 1. 如果是新会话，先创建
  if (!activeSession) {
    const session = await createSessionFromConfig()
    // ...
  }

  // 2. 乐观更新：立即显示用户消息
  addMessage({ role: 'user', content, ... })

  // 3. 开始 SSE 流式
  await stream(
    (signal) => conversationsApi.sendMessage(activeSession.id, content)(signal),
    {
      onChunk: (chunk) => {
        if (chunk.event === 'thought' || chunk.event === 'tool_call') {
          // 追加到当前流式事件列表（用于 ReAct 可视化）
        }
      },
      onDone: () => {
        // 4. 流式完成：将 output 保存为一条 AI 消息
        addMessage({ role: 'assistant', content: output, ... })
        // 5. 刷新会话列表（更新 updated_at）
        loadSessions()
      },
    }
  )
}
```

**流式输出实时追加：** `useSSE` 的 `output` state 实时累积文本，`ConversationView` 渲染时将 `streamingOutput` 作为正在生成中的 AI 消息展示。流式完成后，将完整内容持久化为一条 `DemoMessage`。

---

## 4. 数据流

### 4.1 对话轮次时序图

```
用户输入 "加上错误处理"
        │
        ▼
┌─ 前端 ConversationPage ──────────────────────────────────────────┐
│  1. handleSend("加上错误处理")                                     │
│  2. addMessage({ role: "user", content: "加上错误处理" })  ← 乐观更新│
│  3. conversationsApi.sendMessage(sessionId, content)              │
│     → POST /api/demo-sessions/{id}/messages (SSE)                │
└──────────────────────┬───────────────────────────────────────────┘
                       │
                       ▼
┌─ 后端 DemoSessionController.sendMessage() ────────────────────────┐
│  4. 校验 session 存在 + user_id == currentUser                    │
│  5. DemoMessageService.saveUserMessage(sessionId, content)        │
│     → INSERT INTO demo_messages (role='user', ...)               │
└──────────────────────┬───────────────────────────────────────────┘
                       │
                       ▼
┌─ DemoService.generateDemoWithHistory() ───────────────────────────┐
│  6. 加载用户 AI 配置 (AiConfigService.getActiveConfigEntity)      │
│  7. 构建系统提示词 (从 session 配置)                                │
│  8. 如果 session.kbId != null:                                    │
│     → RAG 预检索: kbService.searchKbVector(userId, kbId, content) │
│     → 注入 systemPrompt += buildRagContext(chunks)                │
│  9. DemoMessageService.getSessionMessages(sessionId)              │
│     → 获取完整消息历史                                              │
│ 10. ContextManager.buildContext(history, systemPrompt, maxTokens) │
│     → 滑动窗口裁剪 + 代码截断 + 摘要注入                            │
│ 11. DemoToolProvider 组装工具 (search_links + search_kb)           │
└──────────────────────┬───────────────────────────────────────────┘
                       │
                       ▼
┌─ ReActAgent.runWithHistory() ─────────────────────────────────────┐
│ 12. messages = new ArrayList<>(contextMessages)  ← 从历史初始化    │
│ 13. runRound() 递归推理循环（与现有逻辑完全一致）                     │
│     → AiProviderAdapter.streamWithTools(systemPrompt, messages)   │
│     → 工具调用 → 结果注入 messages → 下一轮                         │
│ 14. 每个 AiChunk 实时推送到 SSE sink                               │
└──────────────────────┬───────────────────────────────────────────┘
                       │
                       ▼
┌─ 前端 SSE 事件处理 ───────────────────────────────────────────────┐
│ 15. useSSE.onChunk:                                               │
│     thought → 追加到 streamingEvents (ReAct 可视化)                │
│     tool_call → 追加到 streamingEvents                             │
│     text → 追加到 streamingOutput (实时渲染)                       │
│     done → 流式完成                                                │
└──────────────────────┬───────────────────────────────────────────┘
                       │
                       ▼
┌─ 后端 doOnComplete ───────────────────────────────────────────────┐
│ 16. 收集完整 AI 输出                                               │
│ 17. DemoMessageService.saveAssistantMessage(sessionId, fullOutput)│
│     → INSERT INTO demo_messages (role='assistant', metadata={...})│
│ 18. saveDemoSync(): INSERT INTO demos (session_id, message_id)   │
│ 19. 更新 session: message_count++, updated_at = now()             │
│ 20. 保存 RAG 指标 (如果适用)                                       │
└──────────────────────┬───────────────────────────────────────────┘
                       │
                       ▼
┌─ 前端 onDone ─────────────────────────────────────────────────────┐
│ 21. addMessage({ role: "assistant", content: output, metadata })  │
│ 22. loadSessions() → 刷新会话列表排序                               │
│ 23. 清空 streamingOutput / streamingEvents                        │
└──────────────────────────────────────────────────────────────────┘
```

### 4.2 上下文构建流程

```
session.kbId != null?
     │
     ├── Yes → RAG 预检索 (用最新用户消息作为 query)
     │         → 检索结果注入 systemPrompt
     │
     ▼
ContextManager.buildContext(history, systemPrompt, maxTokens)
     │
     ├── history 总 token <= tokenBudget (60%)?
     │     ├── Yes → 直接转换为 ChatMessage 列表
     │     └── No  → 滑动窗口裁剪:
     │               ├── 保留最近 6 轮 (12 条)
     │               ├── 从最早消息开始累加，超出预算的裁剪
     │               ├── 裁剪部分生成摘要 → 注入为 system 消息
     │               └── 超长代码块截断 (> 500 → 前 200 + "[已截断]")
     │
     ▼
finalMessages = [systemPrompt + RAG] + [摘要?] + [保留区消息]
     │
     ▼
ReActAgent.runWithHistory(systemPrompt, finalMessages, tools, handlers, config, ...)
```

### 4.3 流式输出在对话模式下的工作方式

```
ConversationView 渲染逻辑:

messages.map(msg => <MessageBubble key={msg.id} message={msg} />)

// 流式中，额外渲染一个临时气泡:
if (isStreaming) {
  <MessageBubble
    message={{ role: "assistant", content: streamingOutput }}
    isStreaming={true}
  />
  // ReAct 过程:
  streamingEvents.map(evt => <ReActEvent event={evt} />)
}
```

流式完成时，`streamingOutput` 的内容被持久化为一条 `DemoMessage`，临时气泡被替换为正式的消息气泡。

---

## 5. 关键设计决策

### 5.1 新页面 vs 修改现有 DemoPage

**决策：新建 ConversationPage，保留 DemoPage。**

理由：
1. PRD 明确要求"现有 Demo 列表页面功能不受影响"
2. DemoPage 已经有 445 行代码，加入会话逻辑会使其过于臃肿
3. 新页面可以独立演进，不受旧版约束
4. 旧版 Demo 生成作为"快速生成"入口保留，适合简单场景

**共存方案：**
- `/demos` — 旧版单次生成（保留）
- `/conversations` — 新版对话式生成（新增）
- 侧边栏两个入口并存，或在 DemoPage 顶部加"切换到对话模式"入口

### 5.2 向后兼容

| 场景 | 处理方式 |
|------|---------|
| 旧版 Demo 记录 | `demos.session_id = NULL`，继续正常显示 |
| 旧版 API `/api/demos/generate` | 继续工作，不受影响 |
| Token 统计页面 | 同时统计旧版 + 对话式生成的 demos 记录 |
| RAG 指标页面 | 同上 |
| 旧版 Demo 删除 | 不受影响，session_id 外键为 SET NULL |

### 5.3 Token 计数策略

**Phase 1（MVP）：字符估算**
- 复用现有 `estimateTokens()`: `text.length() * 1.5`
- 无需外部依赖，性能好
- 精度足够用于上下文窗口裁剪（裁剪是粗粒度操作）

**Phase 2（后续优化）：tiktoken 或类似方案**
- 如果发现估算偏差导致上下文截断不准确，可引入 tiktoken-jav
- 替换 ContextManager.estimateTokens() 即可，不影响其他模块

### 5.4 上下文压缩时机和策略

**压缩时机：** 每次 `sendMessage` 时，由 `ContextManager.buildContext()` 执行

**压缩策略（按优先级）：**
1. **代码截断**（最低成本）：> 500 字符的代码块 → 前 200 + "...[已截断]"
2. **滑动窗口裁剪**（核心策略）：超出 token 预算时，从最早消息裁剪
3. **摘要注入**（信息保留）：被裁剪的消息以文本摘要形式保留

**不做的事情：**
- 不做 LLM 摘要（增加延迟和成本）
- 不做消息合并（保持消息粒度）
- 不做自动压缩存储（metadata 中的完整数据保留）

### 5.5 工具调用轨迹持久化

**存储位置：** `demo_messages.metadata` JSONB 字段

**存储内容：**
```json
{
  "type": "ai_response",
  "tool_calls": [
    {
      "name": "search_links",
      "args": "{\"query\":\"React error handling\"}",
      "result_preview": "搜索结果前 200 字符..."
    }
  ],
  "react_rounds": 3,
  "tokens_used": 1500,
  "retrieval_chunks": 3
}
```

**不存储完整工具返回结果**（可能很长），只存储预览。完整结果在 ReAct Agent 的 messages 中已有，不需要持久化到 metadata。

**前端渲染：** ReAct 推理过程（思考 + 工具调用）在流式时实时展示，持久化后从 metadata 中读取并以内联折叠方式展示。

### 5.6 重新生成实现

**流程：**
1. 用户点击某条 AI 回复的"重新生成"按钮
2. 前端调用 `POST /api/demo-sessions/{id}/messages/{msgId}/regenerate`
3. 后端执行 `DemoMessageService.deleteFromMessage(sessionId, messageId)`
   - 删除该消息及之后的所有消息
   - 同时删除关联的 demos 记录（或保留，取决于策略）
4. 用删除前的用户消息内容重新走 sendMessage 流程
5. SSE 流式返回新的 AI 回复

**关键点：** 重新生成时保留原始的 RAG 检索配置（来自 session），用相同的用户消息重新执行。

---

## 6. 文件清单

### 6.1 后端新增文件

| 文件路径 | 说明 |
|---------|------|
| `backend/src/main/resources/db/migration/V14__create_demo_sessions.sql` | 创建 demo_sessions + demo_messages 表 |
| `backend/src/main/resources/db/migration/V15__alter_demos_add_session.sql` | demos 表新增 session_id/message_id 外键 |
| `backend/src/main/java/com/devknowledge/model/DemoSession.java` | 会话实体 |
| `backend/src/main/java/com/devknowledge/model/DemoMessage.java` | 消息实体 |
| `backend/src/main/java/com/devknowledge/model/JsonTypeHandler.java` | JSONB ↔ Map 转换 |
| `backend/src/main/java/com/devknowledge/mapper/DemoSessionMapper.java` | 会话 Mapper |
| `backend/src/main/java/com/devknowledge/mapper/DemoMessageMapper.java` | 消息 Mapper |
| `backend/src/main/java/com/devknowledge/controller/DemoSessionController.java` | 会话 API 控制器 |
| `backend/src/main/java/com/devknowledge/service/DemoSessionService.java` | 会话管理服务 |
| `backend/src/main/java/com/devknowledge/service/DemoMessageService.java` | 消息管理服务 |
| `backend/src/main/java/com/devknowledge/service/ContextManager.java` | 上下文窗口管理 |
| `backend/src/main/java/com/devknowledge/dto/DemoSessionRequest.java` | 会话请求 DTO（创建/更新） |
| `backend/src/main/java/com/devknowledge/dto/DemoSessionResponse.java` | 会话响应 DTO |
| `backend/src/main/java/com/devknowledge/dto/SendMessageRequest.java` | 发送消息请求 DTO |
| `backend/src/main/java/com/devknowledge/dto/DemoSessionDetailResponse.java` | 会话详情响应（含消息） |

### 6.2 后端修改文件

| 文件路径 | 变更内容 |
|---------|---------|
| `backend/src/main/java/com/devknowledge/service/ai/ReActAgent.java` | 新增 `runWithHistory()` 方法（~30 行） |
| `backend/src/main/java/com/devknowledge/service/DemoService.java` | 新增 `generateDemoWithHistory()` 方法（~80 行） |
| `backend/src/main/java/com/devknowledge/model/Demo.java` | 新增 `sessionId`、`messageId` 字段 |

### 6.3 前端新增文件

| 文件路径 | 说明 |
|---------|------|
| `frontend/src/pages/ConversationPage.tsx` | 对话主页（双栏布局） |
| `frontend/src/components/conversation/SessionList.tsx` | 左侧会话列表面板 |
| `frontend/src/components/conversation/ChatInput.tsx` | 底部输入区 |
| `frontend/src/components/conversation/MessageBubble.tsx` | 消息气泡组件 |
| `frontend/src/components/conversation/ConversationView.tsx` | 消息列表 + 滚动 |
| `frontend/src/components/conversation/SessionConfigBar.tsx` | 会话配置栏 |
| `frontend/src/components/conversation/ReActTrace.tsx` | ReAct 推理过程折叠展示 |
| `frontend/src/api/conversations.ts` | 会话 + 消息 API 客户端 |
| `frontend/src/stores/conversationStore.ts` | Zustand 状态管理 |

### 6.4 前端修改文件

| 文件路径 | 变更内容 |
|---------|---------|
| `frontend/src/App.tsx` | 新增 `/conversations` 路由 |
| `frontend/src/components/layout/Sidebar.tsx` | 导航菜单新增"对话 Demo"入口 |
| `frontend/src/types/api.ts` | 新增 `DemoSession`、`DemoMessage` 等类型定义 |

### 6.5 不修改的文件（确认保留）

| 文件 | 原因 |
|------|------|
| `frontend/src/pages/DemoPage.tsx` | 旧版单次生成，继续保留 |
| `frontend/src/api/demos.ts` | 旧版 API 客户端 |
| `frontend/src/hooks/useSSE.ts` | 核心逻辑不变，复用 |
| `backend/src/main/java/com/devknowledge/controller/DemoController.java` | 旧版 API 继续保留 |
| `backend/src/main/java/com/devknowledge/service/ai/DemoToolProvider.java` | 工具定义不变 |

---

## 7. 依赖关系与任务排序

### 7.1 任务依赖图

```
Phase 1: 数据层 (无依赖，可并行)
├── T1: V14 迁移脚本 (demo_sessions + demo_messages 表)
├── T2: V15 迁移脚本 (demos 表新增外键)
├── T3: DemoSession + DemoMessage 实体 + JsonTypeHandler
├── T4: DemoSessionMapper + DemoMessageMapper
└── T5: 请求/响应 DTO

Phase 2: 后端服务层 (依赖 Phase 1)
├── T6: ContextManager 服务 (依赖 T3)
├── T7: DemoMessageService (依赖 T4)
├── T8: DemoSessionService (依赖 T4, T7)
├── T9: ReActAgent.runWithHistory() (依赖 T3，无其他依赖)
└── T10: DemoService.generateDemoWithHistory() (依赖 T6, T7, T9)

Phase 3: 后端 API 层 (依赖 Phase 2)
└── T11: DemoSessionController (依赖 T8, T10)

Phase 4: 前端 API + 状态 (依赖 Phase 3)
├── T12: types/api.ts 类型定义
├── T13: api/conversations.ts API 客户端
└── T14: conversationStore.ts Zustand store

Phase 5: 前端组件 (依赖 Phase 4)
├── T15: MessageBubble + ReActTrace (可独立开发)
├── T16: ChatInput (可独立开发)
├── T17: SessionList (可独立开发)
├── T18: SessionConfigBar (可独立开发)
└── T19: ConversationView (依赖 T15)

Phase 6: 前端集成 (依赖 Phase 5)
├── T20: ConversationPage (组装所有组件)
├── T21: App.tsx 路由 + Sidebar 导航
└── T22: 联调测试
```

### 7.2 推荐开发顺序

1. **T1-T5** → 数据层（~1 天）
2. **T6-T10** → 后端服务（~2 天）
3. **T11** → API 控制器（~0.5 天）
4. **T12-T14** → 前端 API + 状态（~1 天）
5. **T15-T19** → 前端组件（~2 天）
6. **T20-T22** → 集成测试（~1 天）

**总计：约 7.5 天**

### 7.3 并行化机会

- T6 (ContextManager) 和 T7 (DemoMessageService) 可并行
- T9 (ReActAgent 改造) 与 T6-T8 可并行
- T15-T18 四个组件可并行开发
- 前端 T12-T14 可在 T11 完成后立即开始（API 接口已确定）

---

## 附录 A：Flyway 迁移脚本预览

### V14__create_demo_sessions.sql

```sql
-- Demo 会话表
CREATE TABLE demo_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(300) NOT NULL DEFAULT '新对话',
    language VARCHAR(50),
    framework_id UUID REFERENCES frameworks(id),
    kb_id UUID REFERENCES knowledge_bases(id),
    top_k INTEGER DEFAULT 3,
    retrieval_source VARCHAR(20) DEFAULT 'none',
    message_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_demo_sessions_user_id ON demo_sessions (user_id);
CREATE INDEX idx_demo_sessions_updated_at ON demo_sessions (updated_at DESC);

-- Demo 消息表
CREATE TABLE demo_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES demo_sessions(id) ON DELETE CASCADE,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    content_type VARCHAR(20) NOT NULL DEFAULT 'text',
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_demo_messages_session_id ON demo_messages (session_id, created_at);
```

### V15__alter_demos_add_session.sql

```sql
-- demos 表新增会话关联字段
ALTER TABLE demos ADD COLUMN session_id UUID REFERENCES demo_sessions(id) ON DELETE SET NULL;
ALTER TABLE demos ADD COLUMN message_id UUID REFERENCES demo_messages(id) ON DELETE SET NULL;
CREATE INDEX idx_demos_session_id ON demos (session_id);
```

---

## 附录 B：ReActAgent.runWithHistory() 实现参考

```java
/**
 * 运行 ReAct 循环（带历史消息上下文）
 * 与 run() 的唯一区别：messages 从 history 初始化而非从空列表开始
 */
public Flux<AiChunk> runWithHistory(String systemPrompt,
                                     List<AiProviderAdapter.ChatMessage> history,
                                     List<AiFunction> tools,
                                     Map<String, ToolHandler> handlers,
                                     UserAiConfig config, int maxIterations,
                                     Map<String, AtomicInteger> toolCallCounts) {

    int effectiveMax = Math.max(1, Math.min(maxIterations, ABSOLUTE_MAX_ITERATIONS));
    log.info("ReAct Agent 启动 (with history), historySize={}, maxIterations={}",
             history.size(), effectiveMax);

    Sinks.Many<AiChunk> sink = Sinks.many().unicast().onBackpressureBuffer();
    AiProviderAdapter adapter = aiProviderFactory.getAdapter(config.getProvider());

    // 关键区别：从历史消息初始化，而非 [new ChatMessage("user", userMessage)]
    List<AiProviderAdapter.ChatMessage> messages =
        Collections.synchronizedList(new ArrayList<>(history));

    AtomicInteger iteration = new AtomicInteger(0);
    List<String> lastRoundSignatures = Collections.synchronizedList(new ArrayList<>());
    AtomicInteger consecutiveAllFail = new AtomicInteger(0);

    // 复用完全相同的 runRound 递归逻辑
    runRound(adapter, systemPrompt, messages, tools, handlers, config, sink,
            iteration, effectiveMax, lastRoundSignatures, consecutiveAllFail, toolCallCounts);

    return sink.asFlux();
}
```

---

## 附录 C：ContextManager 实现参考

```java
@Service
public class ContextManager {

    private static final double TOKEN_BUDGET_RATIO = 0.6;
    private static final int KEEP_RECENT_ROUNDS = 6;
    private static final int CODE_TRUNCATE_THRESHOLD = 500;
    private static final int CODE_TRUNCATE_KEEP = 200;

    /**
     * 构建对话上下文
     * 将消息历史转换为 ChatMessage 列表，处理滑动窗口裁剪
     */
    public List<AiProviderAdapter.ChatMessage> buildContext(
            List<DemoMessage> history, String systemPrompt, int maxTokens) {

        int tokenBudget = (int) (maxTokens * TOKEN_BUDGET_RATIO);

        // 估算总 token
        int totalTokens = history.stream()
                .mapToInt(m -> estimateTokens(m.getContent()))
                .sum();

        List<AiProviderAdapter.ChatMessage> result = new ArrayList<>();

        if (totalTokens <= tokenBudget) {
            // 无需裁剪，直接转换
            for (DemoMessage msg : history) {
                result.add(new AiProviderAdapter.ChatMessage(msg.getRole(), msg.getContent()));
            }
            return result;
        }

        // 需要裁剪：保留最近 N 轮
        int keepCount = Math.min(KEEP_RECENT_ROUNDS * 2, history.size());
        List<DemoMessage> toKeep = history.subList(history.size() - keepCount, history.size());
        List<DemoMessage> toPrune = history.subList(0, history.size() - keepCount);

        // 从 toPrune 末尾向前，尽量多保留（在 token 预算内）
        int usedTokens = toKeep.stream().mapToInt(m -> estimateTokens(m.getContent())).sum();
        List<DemoMessage> extraKeep = new ArrayList<>();
        for (int i = toPrune.size() - 1; i >= 0; i--) {
            int msgTokens = estimateTokens(toPrune.get(i).getContent());
            if (usedTokens + msgTokens > tokenBudget) break;
            extraKeep.add(0, toPrune.get(i));
            usedTokens += msgTokens;
        }

        // 生成裁剪部分摘要
        List<DemoMessage> pruned = toPrune.subList(0, toPrune.size() - extraKeep.size());
        if (!pruned.isEmpty()) {
            String summary = summarizePrunedMessages(pruned);
            result.add(new AiProviderAdapter.ChatMessage("system", summary));
        }

        // 添加额外保留的消息
        for (DemoMessage msg : extraKeep) {
            result.add(new AiProviderAdapter.ChatMessage(msg.getRole(),
                    truncateCodeBlocks(msg.getContent())));
        }

        // 添加必须保留的最近消息
        for (DemoMessage msg : toKeep) {
            result.add(new AiProviderAdapter.ChatMessage(msg.getRole(),
                    truncateCodeBlocks(msg.getContent())));
        }

        return result;
    }

    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        return (int) (text.length() * 1.5);
    }

    public String truncateCodeBlocks(String content) {
        if (content == null || content.length() <= CODE_TRUNCATE_THRESHOLD) return content;
        // 简单策略：如果内容超长，截断
        return content.substring(0, CODE_TRUNCATE_KEEP) + "\n...[已截断]";
    }

    public String summarizePrunedMessages(List<DemoMessage> pruned) {
        StringBuilder sb = new StringBuilder();
        sb.append("（以下是早期对话的摘要，原始内容已被裁剪以节省上下文空间）\n");
        for (DemoMessage msg : pruned) {
            String preview = msg.getContent().length() > 100
                    ? msg.getContent().substring(0, 100) + "..."
                    : msg.getContent();
            sb.append(String.format("- [%s] %s\n", msg.getRole(), preview));
        }
        return sb.toString();
    }
}
```
