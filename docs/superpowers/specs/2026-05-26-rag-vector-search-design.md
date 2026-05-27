# Phase 3b: RAG 向量检索设计文档

> 创建时间：2026-05-26

---

## 1. 背景与目标

当前知识库（Phase 3a）的检索方式是 `LIKE` 模糊匹配，存在以下问题：
- **语义不理解**：搜"React 状态管理"找不到"useState"相关内容
- **整文档返回**：返回整个文档内容，模型需要自己定位相关信息
- **无排序**：LIKE 只能判断包含/不包含，无法按相关度排序

**目标**：引入 Embedding 向量检索 + 文档切分，实现语义级别的知识库搜索，并以混合 RAG 方式介入 Demo 生成。

---

## 2. 核心决策

| 决策项 | 选择 | 原因 |
|--------|------|------|
| RAG 介入方式 | 混合式（预检索注入 + 工具二次搜索） | 保证上下文可见，同时保留模型主动检索能力 |
| Embedding 来源 | **仅 OpenAI Embedding API**（三个可选模型） | DeepSeek/小米等不支持 Embedding 端点，统一用 OpenAI |
| Embedding 配置 | 用户全局配置（独立于 Chat AI 配置） | 专用设置页面，类似 AI 服务配置 |
| 向量存储 | pgvector（PostgreSQL 扩展） | 无需额外组件，适合中小规模 |
| 文档切分 | 按段落切分（\n\n 分割） | 简单可靠，适合代码和结构化文档 |
| 向量维度 | 取决于模型 + 可选 `dimensions` 参数压缩 | 见下方模型表 |
| Embedding 调用 | 批量调用（每批 20 个） | 减少 API 调用次数，更高效 |

### Embedding 模型列表（仅支持 OpenAI）

| 模型 | 默认维度 | 特点 | 最佳实践 |
|------|----------|------|----------|
| `text-embedding-3-small` | 1536 | **成本最低，速度最快** | 通用场景首选，可用 `dimensions` 压缩到 512 |
| `text-embedding-3-large` | 3072 | 效果最好，维度最高 | **必须设置 `dimensions=1536`**（列固定 1536） |
| `text-embedding-ada-002` | 1536 | 上一代模型，不支持 `dimensions` 参数 | 兼容旧系统，新项目不推荐 |

**维度约束**：pgvector 列固定 `vector(1536)`，所有向量必须是 1536 维。
- `small`：默认 1536，直接存储；可压缩到 512 后零填充
- `large`：默认 3072，**创建知识库时必须设置 `dimensions=1536`**，否则 3072 维会被截断丢失信息
- `ada-002`：固定 1536，不支持 `dimensions` 参数，直接存储

**调用参数**：
```json
{
    "model": "text-embedding-3-small",
    "input": "测试文本",
    "dimensions": 512
}
```
- `model`：必填，从上表选择
- `input`：必填，字符串或字符串数组（批量）
- `dimensions`：**可选**，仅 `text-embedding-3-small` 和 `text-embedding-3-large` 支持压缩，`ada-002` 不支持

**注意**：DeepSeek、小米等服务商不支持 `/v1/embeddings` 端点，Embedding 统一调用 OpenAI API。用户需要单独配置 OpenAI API Key。

---

## 3. 架构总览

### 3.1 文档入库流程（异步）

```
文档上传
  → FileParserService 解析为纯文本（现有流程，不变）
  → 段落切分：按 \n\n 分割，合并短段，截断长段
  → 批量 Embedding：每 20 个 chunk 一批调 OpenAI /v1/embeddings
  → 存入 kb_chunks 表（content + embedding vector）
  → 更新 kb_documents.chunk_count
```

### 3.2 Demo 生成流程（混合 RAG）

```
用户提问
  → EmbeddingService 将 query 向量化（调用 OpenAI API）
  → pgvector 余弦相似度检索 top-K（默认 3）
  → 检索结果注入 system prompt 作为参考上下文
  → ReAct Agent 启动（prompt 已含上下文）
  → 模型可调用 search_kb 工具做二次深入检索（返回 top-5）
```

---

## 4. 数据库变更

### V8 迁移：启用 pgvector + 创建 kb_chunks 表

```sql
-- 启用 pgvector 扩展
CREATE EXTENSION IF NOT EXISTS vector;

-- 文档切片表
-- embedding 列固定 vector(1536)：
--   - text-embedding-3-small 默认 1536 → 直接存储
--   - text-embedding-3-large 默认 3072 → 创建知识库时必须设置 dimensions=1536 压缩
--   - text-embedding-ada-002 默认 1536 → 直接存储
--   - 用户选择更小维度（如 512）→ 零填充到 1536
CREATE TABLE kb_chunks (
    id UUID PRIMARY KEY,
    kb_id UUID NOT NULL REFERENCES knowledge_bases(id) ON DELETE CASCADE,
    doc_id UUID NOT NULL REFERENCES kb_documents(id) ON DELETE CASCADE,
    chunk_index INT NOT NULL,           -- 切片在文档中的顺序
    content TEXT NOT NULL,               -- 切片文本内容
    embedding vector(1536),              -- 向量（固定 1536 维）
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_kb_chunks_kb_id ON kb_chunks(kb_id);
CREATE INDEX idx_kb_chunks_doc_id ON kb_chunks(doc_id);

-- HNSW 索引：近似最近邻，支持增量插入（比 IVFFlat 更适合）
CREATE INDEX idx_kb_chunks_embedding ON kb_chunks
    USING hnsw (embedding vector_cosine_ops);
```

### kb_documents 表新增字段

```sql
ALTER TABLE kb_documents ADD COLUMN chunk_count INT DEFAULT 0;
```

### 新增 user_embedding_configs 表

```sql
-- 用户 Embedding 配置（独立于 Chat AI 配置）
-- 仅存储 API 凭证和连接信息，不存储模型/维度（模型维度锁定在知识库级别）
CREATE TABLE user_embedding_configs (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    name VARCHAR(100),                          -- 配置名称
    api_key TEXT NOT NULL,                       -- OpenAI API Key（AES 加密）
    base_url VARCHAR(500) DEFAULT 'https://api.openai.com/v1',
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_user_embedding_configs_active
    ON user_embedding_configs(user_id) WHERE is_active = true;
```

### knowledge_bases 表新增字段

```sql
-- 模型和维度在知识库创建时锁定，后续不可修改
ALTER TABLE knowledge_bases ADD COLUMN embedding_model VARCHAR(100) NOT NULL DEFAULT 'text-embedding-3-small';
ALTER TABLE knowledge_bases ADD COLUMN embedding_dimensions INT;
```

**说明**：
- `embedding_model`：创建时选择，之后不可改。不同模型的向量空间不兼容
- `embedding_dimensions`：创建时选择（可选），之后不可改
- 如果需要换模型/维度，必须新建知识库

### 新增 embedding_usage 表（Token 消耗统计）

```sql
CREATE TABLE embedding_usage (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    config_id UUID NOT NULL REFERENCES user_embedding_configs(id),
    prompt_tokens INT NOT NULL,                 -- 单次调用消耗的 token 数
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_embedding_usage_user_date ON embedding_usage(user_id, created_at);
```

---

## 5. 后端设计

### 5.1 AiProviderAdapter 接口（不变）

```java
public interface AiProviderAdapter {
    // 现有方法，不修改
    Flux<String> streamCompletion(String systemPrompt, String userMessage, UserAiConfig config);
    Flux<AiChunk> streamWithTools(String systemPrompt, List<ChatMessage> messages,
                                   List<AiFunction> tools, UserAiConfig config);
}
```

Embedding 不走适配器层，由 `EmbeddingService` 直接调用 OpenAI API（只有 OpenAI 支持 Embedding 端点）。

### 5.3 EmbeddingService（新增）

```java
@Service
public class EmbeddingService {
    private static final int VECTOR_DIMENSION = 1536;  // pgvector 列固定维度

    /**
     * 批量 Embedding
     *
     * @param texts      要向量化的文本列表（每批最多 20 个）
     * @param baseUrl    API 地址（从 user_embedding_configs 获取）
     * @param apiKey     API Key（从 user_embedding_configs 获取）
     * @param model      模型名（从 knowledge_bases 获取，创建时锁定）
     * @param dimensions 维度（从 knowledge_bases 获取，创建时锁定，可为 null）
     * @return EmbeddingResult（向量列表 + prompt_tokens）
     */
    public EmbeddingResult embedBatch(List<String> texts, String baseUrl, String apiKey,
                                       String model, Integer dimensions) {
        // POST {baseUrl}/embeddings
        // Authorization: Bearer {apiKey}
        //
        // 请求体：
        // {
        //     "model": "text-embedding-3-small",
        //     "input": ["文本1", "文本2", ...],
        //     "dimensions": 512        // 可选，ada-002 不支持
        // }
        //
        // 响应体：
        // {
        //     "data": [
        //         { "embedding": [0.023, -0.156, ...], "index": 0, "object": "embedding" }
        //     ],
        //     "model": "text-embedding-ada-002",
        //     "usage": { "prompt_tokens": 5, "total_tokens": 5 }
        // }
        //
        // 处理逻辑：
        // 1. 调用 API 获取向量
        // 2. 解析 usage.prompt_tokens 返回给调用方（用于统计）
        // 3. 如果返回维度 < 1536，零填充到 1536 维
        // 4. 如果返回维度 > 1536（如 large 的 3072），截断到 1536
        // 5. 401 → API Key 无效；400 "Not supported model" → 模型不支持
    }

    /** Embedding 结果：向量列表 + token 消耗 */
    public record EmbeddingResult(List<float[]> vectors, int promptTokens) {}
}
```

**调用链路**：
- `baseUrl` + `apiKey` ← `user_embedding_configs`（用户全局，可切换）
- `model` + `dimensions` ← `knowledge_bases`（创建时锁定，不可改）

**维度填充逻辑**：
```java
private float[] padToTargetDimension(float[] original, int targetDim) {
    if (original.length == targetDim) return original;
    float[] padded = new float[targetDim];
    System.arraycopy(original, 0, padded, 0, Math.min(original.length, targetDim));
    return padded;  // 多出的位置默认 0.0
}
```

**维度填充逻辑**：
```java
private float[] padToTargetDimension(float[] original, int targetDim) {
    if (original.length == targetDim) return original;
    float[] padded = new float[targetDim];
    System.arraycopy(original, 0, padded, 0, Math.min(original.length, targetDim));
    return padded;  // 多出的位置默认 0.0
}
```

### 5.4 UserEmbeddingConfig 实体

```java
@Data
@TableName("user_embedding_configs")
public class UserEmbeddingConfig {
    @TableId(type = IdType.INPUT)
    private UUID id;
    private UUID userId;
    private String name;            // 配置名称
    private String apiKey;          // AES 加密存储
    private String baseUrl;         // API 地址，默认 https://api.openai.com/v1
    private Boolean isActive;
    private Instant createdAt;
    private Instant updatedAt;
}
// 注意：model 和 dimensions 不在此实体中，锁定在 knowledge_bases 表（创建时确定）
```

### 5.5 EmbeddingConfigService（新增）

```java
@Service
public class EmbeddingConfigService {
    // 结构类似 AiConfigService，包含：
    // - getActiveConfig(userId) → 加载激活配置
    // - getAllConfigs(userId) → 列表（脱敏）
    // - updateConfig(userId, request) → 创建/更新
    // - switchConfig(userId, configId) → 切换
    // - deleteConfig(userId, configId) → 删除
    // - testConfig(userId) → 测试连通性（调用 /v1/embeddings）
    // - getEmbeddingUsage(userId) → 近 7 天 Token 消耗统计
}
```

### 5.6 EmbeddingUsageService（新增）

```java
@Service
public class EmbeddingUsageService {
    /**
     * 记录 Embedding Token 消耗
     * 两种场景都会调用：
     * 1. 文档入库：chunkAndEmbed 中批量向量化文档 chunk
     * 2. 检索查询：searchKbVector 中向量化用户 query
     */
    public void recordUsage(UUID userId, UUID configId, int promptTokens) {
        EmbeddingUsage usage = new EmbeddingUsage();
        usage.setId(UUID.randomUUID());
        usage.setUserId(userId);
        usage.setConfigId(configId);
        usage.setPromptTokens(promptTokens);
        usage.setCreatedAt(Instant.now());
        usageMapper.insert(usage);
    }

    /**
     * 查询近 7 天每日 Token 消耗
     */
    public List<TokenUsage> getWeeklyUsage(UUID userId) { ... }
}
```

**Token 消耗场景**：
| 场景 | 触发时机 | Token 量级 |
|------|---------|-----------|
| 文档入库 | 上传文档后异步处理 | 较大（整篇文档切分后逐批向量化） |
| 检索查询 | 每次 Demo 生成时 query 向量化 | 极小（单条 query，约 5-20 tokens） |

### 5.7 KbChunkSearchResult DTO

```java
@Data
public class KbChunkSearchResult {
    private UUID id;
    private UUID docId;
    private String filename;    // 关联查出
    private Integer chunkIndex;
    private String content;
    private double score;       // 余弦相似度（0-1）
}
```

### 5.5 KbChunk 实体

```java
@Data
@TableName("kb_chunks")
public class KbChunk {
    @TableId(type = IdType.INPUT)
    private UUID id;
    private UUID kbId;
    private UUID docId;
    private Integer chunkIndex;
    private String content;
    // embedding 字段由自定义 SQL 处理，不映射为 Java 字段
    private Instant createdAt;
}
```

### 5.5 KbChunkMapper

```java
@Mapper
public interface KbChunkMapper extends BaseMapper<KbChunk> {

    /**
     * 向量相似度检索：返回 top-K 最相关的 chunk
     * 使用 pgvector 的 <=> (余弦距离) 运算符
     */
    @Select("SELECT id, kb_id, doc_id, chunk_index, content, " +
            "1 - (embedding <=> #{vector}::vector) as score " +
            "FROM kb_chunks " +
            "WHERE kb_id = #{kbId} " +
            "ORDER BY embedding <=> #{vector}::vector " +
            "LIMIT #{topK}")
    List<KbChunkSearchResult> searchByVector(
        @Param("kbId") UUID kbId,
        @Param("vector") String vectorLiteral,
        @Param("topK") int topK
    );
}
```

### 5.6 KbService 改造

新增方法：

```java
/**
 * 文档切分 + 向量化（异步，在文档解析完成后调用）
 * - API 凭证：从 user_embedding_configs 加载（用户全局配置）
 * - 模型/维度：从 knowledge_bases 读取（创建时锁定）
 */
public void chunkAndEmbed(UUID userId, UUID docId, String content, UUID kbId) {
    // 1. 加载知识库（获取锁定的模型和维度）
    KnowledgeBase kb = kbMapper.selectById(kbId);

    // 2. 加载用户激活的 Embedding 配置（获取 API Key）
    UserEmbeddingConfig config = embeddingConfigService.getActiveConfig(userId);
    String apiKey = aes.decrypt(config.getApiKey());

    // 3. 段落切分
    List<String> chunks = splitIntoChunks(content);

    // 4. 批量 Embedding（每批 20 个，批间延迟防速率限制）
    int totalTokens = 0;
    for (List<String> batch : partition(chunks, 20)) {
        EmbeddingResult result = embeddingService.embedBatch(
            batch, config.getBaseUrl(), apiKey,
            kb.getEmbeddingModel(), kb.getEmbeddingDimensions());
        totalTokens += result.promptTokens();
        for (int i = 0; i < batch.size(); i++) {
            saveChunk(kbId, docId, chunkIndex, batch.get(i), result.vectors().get(i));
        }
        if (chunks.size() > 20) {
            Thread.sleep(200); // 多批时才延迟，单批无需等待
        }
    }

    // 5. 记录 Token 消耗
    embeddingUsageService.recordUsage(userId, config.getId(), totalTokens);

    // 6. 更新 doc.chunkCount
}
```

段落切分规则：
- 按 `\n\n`（双换行）分割
- 合并短段：连续段落 < 100 字则合并
- 截断长段：单段 > 1000 字符则按句号/换行再切
- 保留段落顺序（chunk_index）

改造 `searchKb` 方法：优先用向量检索，回退到 LIKE：

```java
public Mono<List<KbChunkSearchResult>> searchKbVector(UUID userId, UUID kbId, String query, int topK) {
    return Mono.fromCallable(() -> {
        // 1. 加载知识库（获取锁定的模型和维度）
        KnowledgeBase kb = kbMapper.selectById(kbId);

        // 2. 加载用户激活的 Embedding 配置（获取 API Key）
        UserEmbeddingConfig config = embeddingConfigService.getActiveConfig(userId);
        String apiKey = aes.decrypt(config.getApiKey());

        // 3. 将 query 向量化（使用知识库锁定的模型和维度）
        EmbeddingResult result = embeddingService.embedBatch(
            List.of(query), config.getBaseUrl(), apiKey,
            kb.getEmbeddingModel(), kb.getEmbeddingDimensions());
        float[] queryVector = result.vectors().get(0);

        // 4. 记录 Token 消耗
        embeddingUsageService.recordUsage(userId, config.getId(), result.promptTokens());

        // 5. pgvector 相似度检索
        return chunkMapper.searchByVector(kbId, vectorToString(queryVector), topK);
    }).subscribeOn(Schedulers.boundedElastic());
}
```

**说明**：
- API 凭证（Key/URL）从 `user_embedding_configs` 加载
- 模型/维度从 `knowledge_bases` 读取（创建时锁定，不可修改）
- query 向量化必须使用与文档相同的模型+维度，否则无法比较

### 5.7 DemoToolProvider 改造

`search_kb` handler 改用向量检索：

```java
private ToolHandler buildSearchKbHandler(UUID kbId) {
    return args -> {
        String query = extractJsonString(args, "query");
        // 用向量检索替代 LIKE
        var results = kbService.searchKbVector(kbId, query, 5).block();
        // 格式化返回
    };
}
```

### 5.8 DemoService 改造

`generateDemo` 中新增预检索注入：

```java
public Flux<ServerSentEvent<String>> generateDemo(UUID userId, GenerateDemoRequest req) {
    // ...加载 AI 配置...

    String systemPrompt = buildSystemPrompt(req);

    // 如果选择了知识库，预检索 top-K 注入 prompt
    if (req.getKbId() != null) {
        int topK = req.getTopK() != null ? req.getTopK() : 3;
        List<KbChunkSearchResult> contextChunks =
            kbService.searchKbVector(req.getKbId(), req.getPrompt(), topK).block();

        if (contextChunks != null && !contextChunks.isEmpty()) {
            systemPrompt += buildRagContext(contextChunks);
        }

        // 保留 search_kb 工具供二次检索
        tools.add(toolProvider.getKbTool());
        handlers.put("search_kb", toolProvider.getKbHandler(req.getKbId()));
    }

    // ...继续 ReAct Agent...
}

private String buildRagContext(List<KbChunkSearchResult> chunks) {
    StringBuilder sb = new StringBuilder();
    sb.append("\n\n以下是知识库中的相关参考内容（已自动检索）：\n");
    sb.append("请优先参考这些内容回答问题，如果信息不足可以调用 search_kb 工具进一步搜索。\n\n");
    for (int i = 0; i < chunks.size(); i++) {
        sb.append(String.format("[%d] 来源: %s (相关度: %.1f%%)\n",
            i + 1, chunks.get(i).getFilename(), chunks.get(i).getScore() * 100));
        sb.append(chunks.get(i).getContent()).append("\n\n");
    }
    return sb.toString();
}
```

---

## 6. 前端设计

### 6.1 GenerateDemoRequest 扩展

```typescript
interface GenerateDemoRequest {
    prompt: string
    frameworkId?: string
    language?: string
    maxIterations?: number
    kbId?: string
    topK?: number          // 新增：预检索数量
}
```

### 6.2 DemoPage 改造

- 当用户选择了知识库时，显示 Top-K 滑块（默认 3，范围 1-10）
- 不选择知识库时隐藏滑块

### 6.3 SettingsPage 改造（侧边栏导航）

从顶部 Tab 改为左侧侧边栏导航，三个独立子页面：

```
┌─────────────────┬──────────────────────────────────────────┐
│ 设置             │                                          │
│                  │                                          │
│ ▸ AI 服务配置    │  （右侧内容区，根据选择显示）             │
│ ▸ Embedding AI  │                                          │
│ ▸ 数据存储       │                                          │
│                  │                                          │
└─────────────────┴──────────────────────────────────────────┘
```

```typescript
type SettingsTab = 'ai' | 'embedding' | 'storage'

const tabs: { key: SettingsTab; label: string; desc: string }[] = [
  { key: 'ai', label: 'AI 服务配置', desc: 'Chat 模型配置' },
  { key: 'embedding', label: 'Embedding AI', desc: '文本向量化模型' },
  { key: 'storage', label: '数据存储', desc: '本地存储设置' },
]
```

### 6.4 EmbeddingSettings 页面（新增）

复用 `AiSettings` 的 UI 模式，管理 OpenAI API 凭证。

**左侧列表**：我的 Embedding 配置（结构同 AiSettings）

**右侧表单**：
- 配置名称（文本输入）
- API Base URL（默认 `https://api.openai.com/v1`，可改）
- API Key（密码输入框，必填）

**注意**：模型和维度不在这里配置，而是在**创建知识库时选择**（锁定后不可更改）。
这里只管理 API 凭证（Key + URL），可以配置多个，切换激活。

**底部**：Embedding Token 消耗柱状图（近 7 天，复用 AiSettings 的图表组件）

**操作按钮**：保存 / 测试连接 / 设为默认 / 删除（同 AiSettings）

### 6.5 KbPage 改造

**创建知识库**时新增 Embedding 模型和维度选择（**创建后不可修改**）：
- API Key / Base URL：来自用户在 "Embedding AI" 设置页的全局配置
- Embedding 模型（下拉，3 选 1）：
  - `text-embedding-3-small`（默认）— 成本最低，速度最快
  - `text-embedding-3-large` — 效果最好，**必须设 dimensions=1536**
  - `text-embedding-ada-002` — 上一代，不支持 dimensions
- 向量维度（数字输入，可选）：
  - `small`：留空=1536，可填 512（推荐）
  - `large`：**必须填 1536**（前端校验，否则提示 "large 模型必须设置 dimensions=1536"）
  - `ada-002`：禁用输入（不支持 dimensions 参数）
- 创建后，知识库详情页显示锁定的模型和维度信息（只读）

**文档管理**：
- 文档列表显示 chunk 数量列
- 文档状态增加 "embedding" 状态（向量化进行中）
- 如果用户未配置 Embedding AI，上传文档时提示 "请先在设置页配置 Embedding AI"

### 6.4 types/api.ts

```typescript
interface KbDocument {
    // 现有字段...
    chunkCount?: number     // 新增：切片数量
}
```

---

## 7. 检索质量评估

### 7.1 离线指标（后续构建测试集）

| 指标 | 含义 | 计算方式 |
|------|------|----------|
| Precision@K | top-K 结果中相关文档的比例 | 相关数 / K |
| Recall@K | 召回了全部相关文档的比例 | 被召回数 / 总相关数 |
| MRR | 第一个相关结果的排名倒数 | 1 / 排名 |
| Hit Rate | top-K 中至少有一个相关的查询比例 | 命中数 / 总查询数 |

### 7.2 在线指标（生产环境监控）

| 指标 | 含义 | 实现方式 |
|------|------|----------|
| 检索延迟 | 向量检索耗时 | 日志记录 p50/p95 |
| RAG 使用率 | 模型引用注入内容的比例 | prompt 中标注来源，检查输出 |
| Token 开销 | 注入上下文消耗的额外 token | 计算 context 字符数 |
| Chunk 数量 | 每个文档的平均切片数 | 存入 chunk_count 字段 |

---

## 8. 新增 API 接口

### Embedding 配置管理

| Method | Path | 说明 |
|--------|------|------|
| GET | `/api/user/embedding-config` | 获取激活的 Embedding 配置（脱敏） |
| GET | `/api/user/embedding-configs` | 获取所有 Embedding 配置列表 |
| PUT | `/api/user/embedding-config` | 创建/更新 Embedding 配置 |
| POST | `/api/user/embedding-configs/{id}/activate` | 切换激活配置 |
| DELETE | `/api/user/embedding-configs/{id}` | 删除配置 |
| POST | `/api/user/embedding-config/test` | 测试连通性 |
| GET | `/api/user/embedding-usage` | 近 7 天 Embedding Token 消耗 |

---

## 9. 实现优先级

| 优先级 | 内容 | 说明 |
|--------|------|------|
| P0 | V8 迁移：pgvector + kb_chunks + user_embedding_configs + embedding_usage | 数据库基础设施 |
| P0 | EmbeddingConfigService + Controller（CRUD + 测试 + Token 统计） | Embedding 配置管理 |
| P0 | EmbeddingService（调用 OpenAI /v1/embeddings + 维度填充） | Embedding 核心能力 |
| P0 | KbService chunkAndEmbed（段落切分 + 批量向量化） | 文档入库 |
| P0 | KbChunkMapper searchByVector | 向量检索 |
| P1 | DemoService 预检索注入 + search_kb 工具改造 | 混合 RAG |
| P1 | 前端 SettingsPage 侧边栏 + EmbeddingSettings 页面 | 配置 UI |
| P1 | 前端 DemoPage top-K 滑块 | 用户可调参数 |
| P1 | 前端 KbPage chunk 数量 + embedding 状态 | 知识库 UI |
| P2 | 检索质量评估 | 构建测试集 + 离线评估 |

---

## 9. 风险与注意事项

1. **Embedding API 费用**：`text-embedding-3-small` 价格 $0.02/1M tokens，一篇 5000 字文档约 $0.00006，成本极低。
2. **OpenAI API Key 依赖**：用户需要在 "Embedding AI" 设置页单独配置 OpenAI API Key（与 Chat 模型 Key 独立）。未配置时上传文档会提示。
3. **维度不一致**：知识库创建时锁定模型+维度，不可修改。不同模型即使维度相同，向量空间也不同，无法混用。需要换模型就新建知识库。
4. **pgvector HNSW 索引**：HNSW 对增量插入天然友好，不需要频繁 `REINDEX`。仅在大量数据删除后（如清空知识库重建）才需要考虑。
5. **Embedding 速率限制**：OpenAI Embedding API 有 QPS 限制，多批时加 200ms 延迟。当前 `parseExecutor` 为 3 线程，足够应对并发上传。
6. **中文分段**：中文文档可能没有 `\n\n` 分隔，需要按段落首行缩进或固定长度兜底。
7. **文档未就绪**：文档上传后异步处理，如果用户在处理完成前发起 Demo 生成，chunk 表为空。此时预检索返回空列表，系统正常降级为无 RAG 的 ReAct 流程。
8. **Embedding 失败回退**：如果 Embedding API 调用失败（如 Key 无效、余额不足），文档状态标记为 error，降级为 LIKE 模糊匹配。
9. **`dimensions` 参数**：`text-embedding-3-small` 和 `large` 支持，`ada-002` 不支持。传了不支持的参数会 400 报错，需要在代码中判断模型类型。
