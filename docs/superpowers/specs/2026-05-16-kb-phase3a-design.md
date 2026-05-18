# 知识库 Phase 3a：基础架构设计

> 2026-05-16

## 背景

Phase 3 总体目标是构建知识库增强检索系统，支持两种策略：
- **RAG 模式**（Phase 3b）：向量切分 + 语义搜索
- **Wiki 模式**（Phase 3c）：结构化知识页面 + LLM 工具浏览

Phase 3a 是基础层，实现知识库 CRUD、文件上传解析、本地目录批量导入，为后续策略层打好数据基础。

## 设计目标

1. 知识库 CRUD（创建/列表/详情/删除）
2. 文档上传 + 内容解析（txt/md/pdf/docx → 纯文本）
3. 本地目录批量导入（前端选目录 → 分批上传 → 后端异步处理）
4. 基础全文搜索（PostgreSQL tsvector，复用现有模式）

## 数据库设计

### 新增迁移 V7

```sql
-- 知识库表
CREATE TABLE knowledge_bases (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    name VARCHAR(100) NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- 文档表
CREATE TABLE kb_documents (
    id UUID PRIMARY KEY,
    kb_id UUID NOT NULL REFERENCES knowledge_bases(id) ON DELETE CASCADE,
    filename VARCHAR(255) NOT NULL,
    file_type VARCHAR(20) NOT NULL,  -- txt/md/pdf/docx
    file_size BIGINT NOT NULL,
    content TEXT,                     -- 解析后的纯文本内容
    content_tsv TSVECTOR,            -- 全文搜索向量
    status VARCHAR(20) DEFAULT 'processing',  -- processing/ready/error
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_kb_documents_kb_id ON kb_documents(kb_id);
CREATE INDEX idx_kb_documents_content ON kb_documents USING GIN(to_tsvector('english', content));
```

## 后端架构

### 新增文件

```
backend/src/main/java/com/devknowledge/
├── controller/KbController.java           # 知识库 REST API
├── service/KbService.java                 # 知识库业务逻辑
├── service/FileParserService.java         # 文件解析（txt/md/pdf/docx → 文本）
├── mapper/KnowledgeBaseMapper.java        # MyBatis Plus Mapper
├── mapper/KbDocumentMapper.java           # MyBatis Plus Mapper
├── model/KnowledgeBase.java               # 实体
├── model/KbDocument.java                  # 实体
└── dto/KbCreateRequest.java               # DTO
```

### FileParserService — 文件解析

接口设计：

```java
public interface FileParserService {
    /** 从文件字节流中提取纯文本内容 */
    String parse(String filename, byte[] content) throws IOException;
}
```

实现策略：
- **txt/md**：直接读取文本内容（UTF-8）
- **pdf**：使用 Apache PDFBox 提取文本
- **docx**：使用 Apache POI 提取文本

依赖新增：

```xml
<dependency>
    <groupId>org.apache.pdfbox</groupId>
    <artifactId>pdfbox</artifactId>
    <version>3.0.3</version>
</dependency>
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.2.5</version>
</dependency>
```

### KbController — REST API

| Method | Path | 说明 |
|--------|------|------|
| POST | `/api/kb` | 创建知识库 |
| GET | `/api/kb` | 获取用户的知识库列表 |
| GET | `/api/kb/{id}` | 获取知识库详情 |
| DELETE | `/api/kb/{id}` | 删除知识库（级联删除文档） |
| POST | `/api/kb/{id}/documents` | 上传单个文档（multipart） |
| POST | `/api/kb/{id}/documents/batch` | 批量上传文档（multipart，最多 10 个） |
| GET | `/api/kb/{id}/documents` | 获取文档列表 |
| DELETE | `/api/kb/documents/{docId}` | 删除文档 |
| GET | `/api/kb/{id}/search?q=xxx` | 全文搜索 |

### 异步处理

文档上传后异步解析（不阻塞请求）：

```java
@Service
public class KbService {
    private final ExecutorService parseExecutor = Executors.newFixedThreadPool(3);

    public KbDocument uploadDocument(UUID kbId, String filename, byte[] content) {
        // 1. 先创建记录（status=processing）
        KbDocument doc = new KbDocument();
        doc.setStatus("processing");
        kbDocumentMapper.insert(doc);

        // 2. 异步解析
        parseExecutor.submit(() -> {
            try {
                String text = fileParserService.parse(filename, content);
                doc.setContent(text);
                doc.setStatus("ready");
            } catch (Exception e) {
                doc.setStatus("error");
                doc.setErrorMessage(e.getMessage());
            }
            kbDocumentMapper.updateById(doc);
        });

        return doc;
    }
}
```

### 搜索实现

Phase 3a 使用 LIKE 模糊匹配（简单可靠，中英文都支持）。
PostgreSQL tsvector 对中文支持不佳（需要 `pg_jieba` 扩展），向量搜索留给 Phase 3b。

```java
public List<KbDocument> searchKb(UUID kbId, String query) {
    return kbDocumentMapper.selectList(
        new LambdaQueryWrapper<KbDocument>()
            .eq(KbDocument::getKbId, kbId)
            .eq(KbDocument::getStatus, "ready")
            .like(KbDocument::getContent, query)
            .last("LIMIT 20")
    );
}
```

> 注：tsvector + GIN 索引仍在建表时创建，为 Phase 3b 做准备。Phase 3a 先用 LIKE，Phase 3b 切换到 pgvector 语义搜索。

## 前端改动

### 已有（无需改动）

- `api/kb.ts` — API 客户端已定义
- `types/api.ts` — KnowledgeBase/KbDocument/KbChunk 类型已定义

### 需要适配

- `pages/KbPage.tsx` — 适配后端实际返回格式（status 字段、批量上传）
- 新增：本地目录选择组件（File System Access API）

### 本地目录导入 UI

```
知识库详情页
├── 文档列表（显示 filename、fileType、status、createdAt）
├── 上传按钮（单文件）
├── 导入本地目录按钮
│   └── 弹窗：选择目录 → 显示文件列表 → 确认导入
└── 搜索框
```

## 限制策略

| 限制项 | 值 | 说明 |
|--------|-----|------|
| 单文件大小 | ≤ 10MB | 防止内存溢出 |
| 支持格式 | txt, md, pdf, docx | 初始支持，后续扩展 |
| 单次批量上传 | ≤ 10 个文件 | 防止请求过大 |
| 后端并发解析 | 3 线程 | 固定线程池 |
| 知识库文档上限 | 200 个/库 | 防止数据库过大 |
| 搜索结果上限 | 20 条 | 性能考虑 |

## 知识库介入 Demo 生成

用户生成 Demo 时可选择是否开启知识库介入。开启后，ReActAgent 在推理过程中会多一个 `search_kb` 工具，可搜索用户知识库中的文档内容。

### 改动点

**`GenerateDemoRequest.java`** — 新增可选字段：
```java
/** 关联知识库 ID（可选，传入后 ReActAgent 会使用知识库搜索工具） */
private UUID kbId;
```

**`DemoService.generateDemo()`** — 条件注入 KB 工具：
```java
List<AiFunction> tools = new ArrayList<>(List.of(SEARCH_LINKS, GET_FRAMEWORK_INFO));
Map<String, ToolHandler> handlers = buildToolHandlers();

if (req.getKbId() != null) {
    tools.add(SEARCH_KB);
    handlers.put("search_kb", buildSearchKbHandler(req.getKbId()));
}
```

**新增工具定义** `SEARCH_KB`：
- 工具名：`search_kb`
- 描述：搜索用户知识库中的文档内容
- 参数：`{ "query": "搜索关键词" }`
- 执行逻辑：调用 `kbService.searchKb(kbId, query)`，返回匹配文档的 content 片段

**前端 `DemoPage.tsx`** — 新增知识库选择器：
- 生成区域增加一个下拉框，列出用户的知识库
- 选择后将 `kbId` 传入 `generate` 请求
- 不选则不传，不启用知识库介入

### ReAct Agent 行为变化

```
未开启 KB：
  search_links → 搜索公共知识链接
  get_framework_info → 获取框架信息

开启 KB 后（多一个工具）：
  search_links → 搜索公共知识链接
  get_framework_info → 获取框架信息
  search_kb → 搜索用户上传的文档内容（优先用于相关性更高的私有知识）
```

## 不改动的部分

- 现有 `knowledge_links` 公共知识库（与用户知识库并存）
- ReAct Agent 核心引擎（工具调用机制已通用，只需注册新工具）

## 验证方式

1. 创建知识库 → 上传 txt/md 文件 → 检查 status 变为 ready
2. 上传 pdf/docx 文件 → 验证文本提取正确
3. 搜索功能 → 输入关键词 → 返回匹配文档
4. 批量上传 → 验证异步处理 + 进度状态
5. 删除知识库 → 验证级联删除文档
6. Demo 生成 → 选择知识库 → 验证 ReActAgent 调用 search_kb 工具
7. Demo 生成 → 不选知识库 → 验证不调用 search_kb
