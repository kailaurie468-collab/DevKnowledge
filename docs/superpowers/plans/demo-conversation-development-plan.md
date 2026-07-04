# Demo 对话化功能 — 开发计划

> **文档版本**: v1.0
> **创建日期**: 2026-06-09
> **基于**: PRD v1.0 + Architecture Design v1.0
> **预计总工时**: 7.5 人天

---

## 1. 任务拆分

共 **23 个任务**，分为 6 个阶段。

### Phase 1: 数据层（无依赖，可并行）— 约 1 天

| ID | 任务名称 | 预估工时 | 输入 | 输出 | 优先级 |
|----|---------|---------|------|------|--------|
| T1 | 创建 demo_sessions + demo_messages 表 | 0.5h | PRD 5.1 数据模型定义 | `V14__create_demo_sessions.sql` 迁移脚本，数据库表创建成功 | P0 |
| T2 | demos 表新增 session_id/message_id 外键 | 0.5h | PRD 5.2 表变更定义 | `V15__alter_demos_add_session.sql` 迁移脚本，外键索引创建成功 | P0 |
| T3 | 实体类 + JsonTypeHandler | 2h | T1 表结构 | `DemoSession.java`、`DemoMessage.java`、`JsonTypeHandler.java` | P0 |
| T4 | Mapper 接口 | 1h | T3 实体类 | `DemoSessionMapper.java`、`DemoMessageMapper.java` | P0 |
| T5 | 请求/响应 DTO | 1.5h | PRD 3.1/3.2 字段定义 | `DemoSessionRequest.java`、`DemoSessionResponse.java`、`SendMessageRequest.java`、`DemoSessionDetailResponse.java` | P0 |

### Phase 2: 后端服务层（依赖 Phase 1）— 约 2 天

| ID | 任务名称 | 预估工时 | 输入 | 输出 | 优先级 |
|----|---------|---------|------|------|--------|
| T6 | ContextManager 上下文管理服务 | 3h | T3 实体类，架构附录C 算法 | `ContextManager.java`，单元测试通过 | P0 |
| T7 | DemoMessageService 消息管理 | 2h | T4 Mapper | `DemoMessageService.java` | P0 |
| T8 | DemoSessionService 会话管理 | 2h | T4 Mapper, T7 消息服务 | `DemoSessionService.java` | P0 |
| T9 | ReActAgent.runWithHistory() 改造 | 1h | T3，架构附录B 实现参考 | `ReActAgent.java` 新增方法，旧版 run() 不受影响 | P0 |
| T10 | DemoService 对话式生成方法 | 3h | T6, T7, T9，现有 DemoService | `DemoService.java` 新增 `generateDemoWithHistory()` | P0 |

### Phase 3: 后端 API 层（依赖 Phase 2）— 约 0.5 天

| ID | 任务名称 | 预估工时 | 输入 | 输出 | 优先级 |
|----|---------|---------|------|------|--------|
| T11 | DemoSessionController 会话 API | 3h | T8, T10, T5 DTO | `DemoSessionController.java`，所有端点可通过 HTTP 调用 | P0 |

### Phase 4: 前端 API + 状态（依赖 T11）— 约 1 天

| ID | 任务名称 | 预估工时 | 输入 | 输出 | 优先级 |
|----|---------|---------|------|------|--------|
| T22 | API client 新增 patch 方法 | 0.5h | 无依赖，可提前 | `api/client.ts` 新增 `patch<T>()` 方法 | P0 |
| T12 | TypeScript 类型定义 | 1h | T5 DTO 结构 | `types/api.ts` 新增 `DemoSession`、`DemoMessage` 等类型 | P0 |
| T13 | API 客户端 | 1.5h | T12 类型，T22 patch 方法 | `api/conversations.ts` | P0 |
| T14 | Zustand 状态管理 | 3h | T13 API 客户端 | `stores/conversationStore.ts` | P0 |

### Phase 5: 前端组件（依赖 Phase 4）— 约 2 天

| ID | 任务名称 | 预估工时 | 输入 | 输出 | 优先级 |
|----|---------|---------|------|------|--------|
| T15 | MessageBubble + ReActTrace 组件 | 3h | T12 类型 | `MessageBubble.tsx`、`ReActTrace.tsx` | P0 |
| T16 | ChatInput 输入组件 | 2h | — | `ChatInput.tsx` | P0 |
| T17 | SessionList 会话列表组件 | 2h | T12 类型 | `SessionList.tsx` | P0 |
| T18 | SessionConfigBar 配置栏组件 | 2h | T12 类型 | `SessionConfigBar.tsx` | P1 |
| T19 | ConversationView 消息列表 | 2h | T15 MessageBubble | `ConversationView.tsx` | P0 |

### Phase 6: 集成与收尾（依赖 Phase 5）— 约 1 天

| ID | 任务名称 | 预估工时 | 输入 | 输出 | 优先级 |
|----|---------|---------|------|------|--------|
| T20 | ConversationPage 主页面组装 | 3h | T14-T19 所有组件 | `ConversationPage.tsx`，页面可渲染 | P0 |
| T21 | 路由 + 侧边栏导航 | 1h | T20 页面 | `App.tsx` 路由新增，`Sidebar.tsx` 导航入口 | P0 |
| T23 | 联调测试 + Bug 修复 | 3h | 全部前后端代码 | 端到端对话流程跑通，SSE 流式正常 | P0 |

---

## 2. 文件清单

### 2.1 后端新增文件

| 文件路径 | 所属任务 | 说明 |
|---------|---------|------|
| `backend/src/main/resources/db/migration/V14__create_demo_sessions.sql` | T1 | 创建 demo_sessions + demo_messages 表 |
| `backend/src/main/resources/db/migration/V15__alter_demos_add_session.sql` | T2 | demos 表新增 session_id/message_id 外键 |
| `backend/src/main/java/com/devknowledge/model/DemoSession.java` | T3 | 会话实体类 |
| `backend/src/main/java/com/devknowledge/model/DemoMessage.java` | T3 | 消息实体类 |
| `backend/src/main/java/com/devknowledge/model/JsonTypeHandler.java` | T3 | JSONB ↔ Map<String, Object> 转换器 |
| `backend/src/main/java/com/devknowledge/mapper/DemoSessionMapper.java` | T4 | 会话 Mapper 接口 |
| `backend/src/main/java/com/devknowledge/mapper/DemoMessageMapper.java` | T4 | 消息 Mapper 接口 |
| `backend/src/main/java/com/devknowledge/dto/CreateSessionRequest.java` | T5 | 创建会话请求 |
| `backend/src/main/java/com/devknowledge/dto/UpdateSessionRequest.java` | T5 | 更新会话请求 |
| `backend/src/main/java/com/devknowledge/dto/DemoSessionResponse.java` | T5 | 会话列表响应 |
| `backend/src/main/java/com/devknowledge/dto/DemoSessionDetailResponse.java` | T5 | 会话详情响应（含消息） |
| `backend/src/main/java/com/devknowledge/dto/SendMessageRequest.java` | T5 | 发送消息请求 |
| `backend/src/main/java/com/devknowledge/service/ContextManager.java` | T6 | 上下文窗口管理 + 压缩 |
| `backend/src/main/java/com/devknowledge/service/DemoMessageService.java` | T7 | 消息 CRUD + 持久化 |
| `backend/src/main/java/com/devknowledge/service/DemoSessionService.java` | T8 | 会话管理服务 |
| `backend/src/main/java/com/devknowledge/controller/DemoSessionController.java` | T11 | 会话 REST API 控制器 |

### 2.2 后端修改文件

| 文件路径 | 所属任务 | 变更内容 |
|---------|---------|---------|
| `backend/src/main/java/com/devknowledge/service/ai/ReActAgent.java` | T9 | 新增 `runWithHistory()` 方法（约 30 行） |
| `backend/src/main/java/com/devknowledge/service/DemoService.java` | T10 | 新增 `generateDemoWithHistory()` 方法（约 80 行） |
| `backend/src/main/java/com/devknowledge/model/Demo.java` | T2 | 新增 `sessionId`、`messageId` 字段 |

### 2.3 前端新增文件

| 文件路径 | 所属任务 | 说明 |
|---------|---------|------|
| `frontend/src/pages/ConversationPage.tsx` | T20 | 对话主页（双栏布局） |
| `frontend/src/components/conversation/SessionList.tsx` | T17 | 左侧会话列表面板 |
| `frontend/src/components/conversation/ChatInput.tsx` | T16 | 底部输入区（多行 + 发送/停止） |
| `frontend/src/components/conversation/MessageBubble.tsx` | T15 | 消息气泡（用户/AI） |
| `frontend/src/components/conversation/ConversationView.tsx` | T19 | 消息列表 + 自动滚动 |
| `frontend/src/components/conversation/SessionConfigBar.tsx` | T18 | 会话配置栏（语言/框架/KB） |
| `frontend/src/components/conversation/ReActTrace.tsx` | T15 | ReAct 推理过程折叠展示 |
| `frontend/src/api/conversations.ts` | T13 | 会话 + 消息 API 客户端 |
| `frontend/src/stores/conversationStore.ts` | T14 | Zustand 状态管理 |

### 2.4 前端修改文件

| 文件路径 | 所属任务 | 变更内容 |
|---------|---------|---------|
| `frontend/src/App.tsx` | T21 | 新增 `/conversations` 路由，懒加载 ConversationPage |
| `frontend/src/components/layout/Sidebar.tsx` | T21 | links 数组新增 `{ to: '/conversations', label: '对话 Demo', icon: ' ' }` |
| `frontend/src/types/api.ts` | T12 | 新增 `DemoSession`、`DemoMessage`、`DemoSessionDetail` 类型 |
| `frontend/src/api/client.ts` | T22 | 新增 `patch<T>()` 方法（当前只有 get/post/put/delete/stream） |

### 2.5 不修改的文件（确认保留）

| 文件 | 原因 |
|------|------|
| `frontend/src/pages/DemoPage.tsx` | 旧版单次生成，继续保留 |
| `frontend/src/api/demos.ts` | 旧版 API 客户端 |
| `frontend/src/hooks/useSSE.ts` | 核心逻辑不变，直接复用 |
| `backend/src/main/java/com/devknowledge/controller/DemoController.java` | 旧版 API 继续保留 |
| `backend/src/main/java/com/devknowledge/service/ai/DemoToolProvider.java` | 工具定义不变 |
| `backend/src/main/java/com/devknowledge/service/ai/AiProviderAdapter.java` | 适配器接口不变 |

---

## 3. 实现要点

### T1: Flyway 迁移脚本 — demo_sessions + demo_messages

```sql
-- V14__create_demo_sessions.sql
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

**注意**: Flyway 迁移脚本一旦提交不可修改（校验和机制），务必确保脚本正确后再提交。

### T3: JsonTypeHandler 实现要点

MyBatis Plus 处理 JSONB 字段需要自定义 TypeHandler：

```java
@MappedTypes(Map.class)
public class JsonTypeHandler extends BaseTypeHandler<Map<String, Object>> {
    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i,
                                     Map<String, Object> parameter, JdbcType jdbcType)
            throws SQLException {
        try {
            ps.setObject(i, mapper.writeValueAsString(parameter), Types.OTHER);
        } catch (JsonProcessingException e) {
            throw new SQLException("Failed to serialize JSON", e);
        }
    }

    @Override
    public Map<String, Object> getNullableResult(ResultSet rs, String columnName)
            throws SQLException {
        return parseJson(rs.getString(columnName));
    }
    // ... 其他 getNullableResult 重载方法类似

    private Map<String, Object> parseJson(String json) {
        if (json == null) return null;
        try {
            return mapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse JSONB: {}", json, e);
            return new HashMap<>();
        }
    }
}
```

**实体类使用注解**:
```java
@TableField(typeHandler = JsonTypeHandler.class)
private Map<String, Object> metadata;
```

### T3: 实体类 — 代码库模式参考

实体类需遵循现有 `Demo.java` 模式：

```java
@Data
@TableName("demo_sessions")
public class DemoSession {
    @TableId(type = IdType.INPUT)
    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID id;
    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID userId;
    // ... 其他字段
    private Instant createdAt;
    private Instant updatedAt;
}
```

`UuidTypeHandler` 已存在于 `com.devknowledge.model.UuidTypeHandler`，直接复用。

### T6: ContextManager 上下文压缩算法

关键边界情况：
- **空历史**: 返回空列表，不注入摘要
- **仅 1 条消息**: 直接返回，不裁剪
- **全部消息都超预算**: 保留最近 6 轮，对更早的消息全部生成摘要
- **代码截断**: 只截断超过 500 字符的**内容整体**，不做代码块精确解析（MVP 阶段简单策略）

**坑点**: `estimateTokens()` 使用 `text.length() * 1.5`，对中文字符可能偏低（中文 1 字符约 2-3 token）。MVP 阶段可接受，后续可替换为 tiktoken。

### T9: ReActAgent.runWithHistory() 改造

**核心要点**: 与现有 `run()` 方法的唯一区别是 messages 列表的初始化方式。后续的 `runRound()` 递归逻辑完全复用。

```java
// run() 中:
List<ChatMessage> messages = Collections.synchronizedList(new ArrayList<>());
messages.add(new ChatMessage("user", userMessage));

// runWithHistory() 中:
List<ChatMessage> messages = Collections.synchronizedList(new ArrayList<>(history));
```

**注意**: 历史消息中可能包含 `assistant` 角色的工具调用文本描述，这不会影响 ReAct 循环，因为 AI Provider 只关注 role 和 content。

### T10: DemoService.generateDemoWithHistory() 流程

与现有 `generateDemo()` 的关键差异：
1. 系统提示词从 **session 配置**构建（而非 request 参数）
2. RAG 检索使用 **session.kbId**，query 使用最新用户消息
3. 调用 `reactAgent.runWithHistory()` 而非 `reactAgent.run()`
4. `doOnComplete` 中同时保存到 `demo_messages` 表和 `demos` 表
5. 完成后更新 `session.updated_at` 和 `message_count`

**坑点**: 流式完成后的 `doOnComplete` 是在 SSE 流结束后异步执行的，需要确保数据库操作在 `Schedulers.boundedElastic()` 上运行。

**现有代码复用**: `buildSystemPrompt`、`buildRagContext`、`saveDemoSync`、`extractCodeBlocks`、`generateTitle`、`estimateTokens` 均为 `DemoService` 的 private 方法。`generateDemoWithHistory` 需要访问它们，有两种方案：
1. 改为 package-private（去掉 private 修饰符）
2. 将共享逻辑提取到 `DemoService` 内部的公共方法

推荐方案 2，避免暴露内部实现。

**注意**: 现有 `GenerateDemoRequest.java` 缺少 `retrievalSource` 字段（前端 `DemoPage.tsx` 使用了但后端 DTO 未定义）。T10 的 `SendMessageRequest` 应包含此字段。

### T11: DemoSessionController — sendMessage 端点

这是最复杂的端点，流程：
1. 校验 session 存在 + `user_id == currentUser`
2. 保存用户消息到 `demo_messages`
3. 加载会话消息历史
4. ContextManager 构建上下文
5. 如果 `session.kbId != null`，执行 RAG 预检索
6. 调用 `reactAgent.runWithHistory()`
7. 流式返回 SSE 事件
8. `doOnComplete`: 保存 AI 回复 + 更新 session

```java
@PostMapping(value = "/{id}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<String>> sendMessage(
        @PathVariable UUID id,
        @RequestBody SendMessageRequest request,
        @AuthenticationPrincipal JwtUser user) {
    return demoSessionService.getSession(user.getId(), id)
        .switchIfEmpty(Mono.error(new NotFoundException("会话不存在")))
        .flatMapMany(session ->
            demoMessageService.saveUserMessage(id, request.getContent())
                .thenMany(demoService.generateDemoWithHistory(
                    user.getId(), id, request.getContent(), session))
        );
}
```

### T22: API client patch 方法

当前 `frontend/src/api/client.ts` 只有 `get`、`post`、`put`、`delete`、`stream` 方法，缺少 `patch`。T8 DemoSessionService 的 `updateSession` 需要 PATCH 语义。

```typescript
patch<T>(endpoint: string, body?: unknown) {
  return this.request<T>(endpoint, {
    method: 'PATCH',
    body: body ? JSON.stringify(body) : undefined,
  })
}
```

### T14: conversationStore 设计要点

**乐观更新**: 用户发送消息时，立即在前端显示用户消息气泡，不等后端确认。

**流式状态管理**: 使用 `streamingOutput` 和 `streamingEvents` 两个 state 跟踪实时输出。流式完成后，将完整内容合并为一条 `DemoMessage`。

**会话切换**: 切换会话时需要：
1. 清空 `streamingOutput` / `streamingEvents`
2. 加载新会话的 `messages`
3. 更新 `activeSession`

```typescript
// Zustand store 核心结构
const useConversationStore = create<ConversationState>()(
  devtools(
    immer((set, get) => ({
      sessions: [],
      activeSession: null,
      messages: [],
      isStreaming: false,
      streamingOutput: '',
      streamingEvents: [],
      // ... actions
    }))
  )
)
```

### T15: MessageBubble 中 ReAct 推理过程展示

AI 消息中的 `metadata.tool_calls` 需要以内联折叠方式展示：

```tsx
// 折叠面板默认收起
const [expanded, setExpanded] = useState(false)

// 仅 AI 消息且有 tool_calls 时显示
{message.role === 'assistant' && metadata?.tool_calls?.length > 0 && (
  <div className="border rounded-lg p-2 mt-2">
    <button onClick={() => setExpanded(!expanded)} className="text-sm text-gray-500">
      {expanded ? '收起' : '展开'}推理过程 ({metadata.tool_calls.length} 次工具调用)
    </button>
    {expanded && <ReActTrace toolCalls={metadata.tool_calls} />}
  </div>
)}
```

### T16: ChatInput 键盘事件处理

```tsx
const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault() // 阻止默认换行
    if (input.trim() && !isStreaming) {
      onSend(input.trim())
      setInput('')
    }
  }
}
```

**坑点**: `textarea` 的 `value` 受控模式下，`Enter` 会先触发换行再触发事件，必须用 `e.preventDefault()` 阻止。

### T20: ConversationPage 组装要点

流式输出时的渲染逻辑：

```tsx
// 消息列表
{messages.map(msg => <MessageBubble key={msg.id} message={msg} />)}

// 流式中的临时气泡
{isStreaming && (
  <MessageBubble
    message={{
      id: 'streaming',
      role: 'assistant',
      content: streamingOutput,
      contentType: 'mixed',
      metadata: { type: 'ai_response', tool_calls: streamingToolCalls }
    }}
    isStreaming={true}
  />
)}
```

**坑点**: 流式完成后，需要将 `streamingOutput` 转换为一条正式的 `DemoMessage` 并清空流式状态。这个时序要与后端 `doOnComplete` 的保存操作对齐。

---

## 4. 测试策略

### T6: ContextManager 单元测试

**测试文件**: `backend/src/test/java/com/devknowledge/service/ContextManagerTest.java`

| 测试用例 | 输入 | 预期输出 |
|---------|------|---------|
| 空历史 | `[]` | 返回空列表 |
| 单条消息 | `[userMsg]` | 直接返回 1 条 |
| 未超预算 | 5 条消息，总 token < budget | 直接返回 5 条 |
| 触发裁剪 | 20 条消息，总 token > budget | 摘要 + 最近 12 条 |
| 代码截断 | 含 600 字符代码块的消息 | 内容被截断为 200 + "...[已截断]" |
| 全部超预算 | 100 条消息 | 摘要 + 最近 12 条 |
| estimateTokens | "hello world" | 16 (11 * 1.5) |

### T9: ReActAgent.runWithHistory() 测试

**测试文件**: `backend/src/test/java/com/devknowledge/service/ai/ReActAgentTest.java`

| 测试用例 | 说明 |
|---------|------|
| 带空历史运行 | 验证等价于 run() 的行为 |
| 带多轮历史运行 | 验证 AI 能理解上下文 |
| 历史中含工具调用 | 验证不影响 ReAct 循环 |
| 旧版 run() 不受影响 | 回归测试 |

### T7/T8: Service 层测试

**测试文件**:
- `backend/src/test/java/com/devknowledge/service/DemoMessageServiceTest.java`
- `backend/src/test/java/com/devknowledge/service/DemoSessionServiceTest.java`

| 测试用例 | 说明 |
|---------|------|
| 创建会话 | 验证默认字段值 |
| 会话列表分页 | 验证排序和分页 |
| 消息保存 + 查询 | 验证 JSONB metadata 序列化 |
| 删除会话级联 | 验证消息被级联删除 |
| 用户隔离 | 验证用户 A 不能访问用户 B 的会话 |

### T14: conversationStore 测试

**测试文件**: `frontend/src/stores/conversationStore.test.ts`

| 测试用例 | 说明 |
|---------|------|
| 加载会话列表 | 验证 state 更新 |
| 选择会话 | 验证 activeSession + messages 更新 |
| 发送消息乐观更新 | 验证用户消息立即显示 |
| 流式输出累积 | 验证 streamingOutput 正确拼接 |
| 流式完成转换 | 验证 streamingOutput → 正式 message |
| 中断生成 | 验证 AbortController 触发 |

### T23: 端到端联调测试

| 测试场景 | 步骤 | 验证点 |
|---------|------|--------|
| 首次对话 | 输入 prompt → 发送 | 会话自动创建，左侧列表出现新会话，AI 流式回复 |
| 多轮追问 | "加上 localStorage" | AI 引用之前的代码上下文 |
| 会话恢复 | 切换到历史会话 | 消息列表完整显示，可继续输入 |
| 会话删除 | 删除会话 | 列表移除，消息级联删除 |
| RAG 集成 | 选择知识库 + 发送 | RAG 检索结果注入，metadata 记录 chunk 数 |
| 中断生成 | 流式中点击停止 | 已生成内容保留，标记 `interrupted: true` |
| 旧版兼容 | 访问 /demos 页面 | 功能正常，不受影响 |

---

## 5. 依赖关系图

```
Phase 1: 数据层 (无依赖，可并行)
┌──────────────────────────────────────────────────────────┐
│  T1 (迁移脚本)   T2 (迁移脚本)   T3 (实体)   T4 (Mapper)  T5 (DTO) │
│    V14             V15           ↑T1        ↑T3          ↑T3  │
└──────────────────────────────────────────────────────────┘
                    │                │           │           │
                    ▼                ▼           ▼           ▼
Phase 2: 后端服务层
┌──────────────────────────────────────────────────────────┐
│  T6 (ContextManager) ← T3                                │
│  T7 (DemoMessageService) ← T4                            │
│  T8 (DemoSessionService) ← T4, T7                        │
│  T9 (ReActAgent.runWithHistory) ← T3                     │
│  T10 (DemoService.generateDemoWithHistory) ← T6, T7, T9  │
└──────────────────────────────────────────────────────────┘
                    │                │           │           │
                    ▼                ▼           ▼           ▼
Phase 3: 后端 API 层
┌──────────────────────────────────────────────────────────┐
│  T11 (DemoSessionController) ← T8, T10                   │
└──────────────────────────────────────────────────────────┘
                              │
                              ▼
Phase 4: 前端 API + 状态
┌──────────────────────────────────────────────────────────┐
│  T22 (API client patch) — 无依赖，可提前                    │
│  T12 (类型定义) ← T11 的接口结构                           │
│  T13 (API 客户端) ← T12, T22                              │
│  T14 (Zustand Store) ← T13                               │
└──────────────────────────────────────────────────────────┘
                              │
                              ▼
Phase 5: 前端组件 (T15-T18 可并行)
┌──────────────────────────────────────────────────────────┐
│  T15 (MessageBubble + ReActTrace) ← T12                  │
│  T16 (ChatInput)             无外部依赖                    │
│  T17 (SessionList)           ← T12                       │
│  T18 (SessionConfigBar)      ← T12                       │
│  T19 (ConversationView)      ← T15                       │
└──────────────────────────────────────────────────────────┘
                    │
                    ▼
Phase 6: 集成
┌──────────────────────────────────────────────────────────┐
│  T20 (ConversationPage) ← T14, T15, T16, T17, T18, T19   │
│  T21 (路由 + 侧边栏) ← T20                               │
│  T23 (联调测试) ← 全部                                    │
└──────────────────────────────────────────────────────────┘
```

---

## 6. 执行顺序

### 推荐开发顺序

```
Day 1:  T1 → T2 → T3 → T4 → T5     (数据层，1天)
Day 2:  T6 → T7 → T9                 (服务层前半，可并行)
Day 3:  T8 → T10                     (服务层后半)
Day 4:  T11 → T12                    (API层 + 类型定义)
Day 5:  T22 → T13 → T14             (API client patch + 前端 API + 状态)
Day 6:  T15 → T16 → T17 → T18       (前端组件，可并行)
Day 7:  T19 → T20 → T21             (组件集成)
Day 8:  T23                          (联调测试 + Bug修复)
```

### 顺序理由

1. **数据层先行**: 数据库表是所有后端服务的基础，Flyway 迁移脚本必须先就位
2. **服务层按依赖链**: T6/T7/T9 无互相依赖可并行；T8 依赖 T7；T10 依赖 T6/T7/T9
3. **API 层在服务层之后**: Controller 依赖 Service，DTO 可与 Service 并行但接口确定后更准确
4. **前端类型定义紧跟 API**: 接口确定后立即定义 TypeScript 类型，前后端可并行开发
5. **组件并行开发**: T15-T18 互相独立，可并行加速
6. **集成收尾**: 最后组装 + 联调，发现并修复集成问题

### 并行化机会

- **T6, T7, T9**: 可由不同人并行开发
- **T15, T16, T17, T18**: 四个组件完全独立
- **T22 (API client patch) 无依赖**: 可在 Phase 1 就提前完成
- **前端 T12-T14 可在 T11 完成后立即开始**（API 接口已确定）

---

## 7. 风险点

### 风险 1: Flyway 迁移脚本错误

**风险等级**: 高
**描述**: Flyway 校验和机制要求迁移脚本一旦提交不可修改。如果脚本有语法错误或字段遗漏，需要创建新的迁移脚本修复。
**缓解措施**:
- 迁移脚本编写后，先在本地数据库手动执行验证
- 使用 `mvn flyway:validate` 校验脚本
- 提交前 review 表结构是否与实体类一致

### 风险 2: ReActAgent 改造影响旧版功能

**风险等级**: 中
**描述**: 修改 ReActAgent 可能意外影响现有 `run()` 方法的行为。
**缓解措施**:
- 采用**新增方法**而非修改现有方法的策略
- `runWithHistory()` 是纯粹的新增代码，不触碰 `run()` 的任何逻辑
- 改造后运行旧版 Demo 生成的回归测试

### 风险 3: 上下文压缩导致 AI 理解偏差

**风险等级**: 中
**描述**: 代码截断和消息裁剪可能导致 AI 丢失关键上下文，生成质量下降。
**缓解措施**:
- 代码截断阈值 (500 字符) 和保留量 (200 字符) 可通过配置调整
- 保留最近 6 轮消息作为硬性下限
- 摘要注入保留被裁剪消息的关键信息
- 后续可引入 LLM 摘要替代简单文本截取

### 风险 4: SSE 流式输出在对话模式下的时序问题

**风险等级**: 中
**描述**: 流式输出的 `doOnComplete` 回调中保存消息到数据库，如果此时用户快速切换会话或发送新消息，可能导致状态不一致。
**缓解措施**:
- 流式过程中禁用输入框和会话切换
- 使用 AbortController 支持中断
- `doOnComplete` 中的数据库操作使用 `Schedulers.boundedElastic()` 包装

### 风险 5: JSONB metadata 序列化兼容性

**风险等级**: 低
**描述**: MyBatis Plus 的 JSONB TypeHandler 可能与现有项目中其他 JSON 处理方式不一致。
**缓解措施**:
- 参考项目中已有的 JSON 处理方式（如有）
- 单独测试 TypeHandler 的序列化/反序列化
- 处理 null 值和空 JSON 对象的边界情况

### 风险 6: 前端组件复杂度超预期

**风险等级**: 低
**描述**: MessageBubble 组件需要处理多种内容类型（纯文本、代码块、ReAct 推理过程、流式输出），复杂度可能超出 3 小时的预估。
**缓解措施**:
- 先实现纯文本 + 代码块的基本渲染
- ReAct 折叠展示作为增量功能
- 复用现有 `MarkdownOutput` 和 `CodeBlock` 组件

### 风险 7: 前后端联调接口不一致

**风险等级**: 低
**描述**: DTO 字段命名、SSE 事件格式等前后端不一致导致联调耗时。
**缓解措施**:
- T5 DTO 和 T12 TypeScript 类型尽量同步开发
- 使用 OpenAPI/Swagger 生成前端类型（如项目已配置）
- T11 Controller 完成后立即进行 Smoke Test

---

## 附录: 任务 Checklist

### Phase 1 完成标准
- [ ] V14 迁移脚本执行成功，demo_sessions + demo_messages 表已创建
- [ ] V15 迁移脚本执行成功，demos 表新增 session_id/message_id 字段
- [ ] DemoSession/DemoMessage 实体类编译通过
- [ ] JsonTypeHandler 单元测试通过
- [ ] Mapper 接口可注入

### Phase 2 完成标准
- [ ] ContextManager 单元测试全部通过
- [ ] DemoMessageService CRUD 操作正常
- [ ] DemoSessionService 会话管理正常
- [ ] ReActAgent.runWithHistory() 可正常调用，旧版 run() 回归测试通过
- [ ] DemoService.generateDemoWithHistory() SSE 流式输出正常

### Phase 3 完成标准
- [ ] 所有 API 端点可通过 curl/Postman 调用
- [ ] 会话列表分页正常
- [ ] 发送消息 SSE 流式响应正常
- [ ] 会话删除级联正常

### Phase 4 完成标准
- [ ] TypeScript 类型编译通过
- [ ] API 客户端方法可调用
- [ ] Zustand store 基本操作正常

### Phase 5 完成标准
- [ ] 各组件独立渲染正常
- [ ] MessageBubble 支持用户/AI 两种样式
- [ ] ChatInput Enter 发送 / Shift+Enter 换行正常
- [ ] SessionList 会话切换正常
- [ ] ConversationView 自动滚动正常

### Phase 6 完成标准
- [ ] ConversationPage 双栏布局正常
- [ ] 首次输入自动创建会话
- [ ] 多轮对话上下文正确传递
- [ ] 会话恢复 + 继续对话正常
- [ ] 旧版 /demos 页面不受影响
- [ ] 深色模式适配正常
