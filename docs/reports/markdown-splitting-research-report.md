# Markdown 文档拆分方案研究

> 更新时间：2026-05-31 | 目标平台：Spring Boot WebFlux + MyBatis Plus + PostgreSQL pgvector

---

## 1. 拆分策略概述

### 1.1 基于标题的结构化拆分

最广泛采用的方案。在标题边界（`#` 到 `######`）处拆分，将每个章节视为独立的语义单元。

**算法（LangChain 的 MarkdownHeaderTextSplitter）：**
1. 通过正则检测以 `#` 开头的行
2. 在每个标题边界处拆分
3. 维护一个标题层级字典（运行时更新）
4. 当遇到级别 N 的新标题时，覆盖级别 N 并清除所有更深级别
5. 每个 chunk 继承完整的章节路径元数据（如 `H1 > H2 > H3`）

**优点：** 保持语义连贯性；标题是天然的主题边界
**缺点：** 不强制 chunk 大小限制；小章节可能产生过小 chunk，大章节可能超出 embedding 模型上下文窗口

### 1.2 递归大小拆分

按优先级尝试一系列分隔符，当 chunk 超过目标大小时回退到更小的分隔符。

**Markdown 分隔符优先级：**
```
\n## > \n### > \n#### > \n##### > \n###### > \n```\n > \n\n > \n > ' ' > ''
```

**优点：** 保证 chunk 大小约束；优先保留较大的结构边界
**缺点：** 可能在章节中间拆分，破坏语义连贯性；不附带结构元数据

### 1.3 两阶段管道（推荐）

结合两种方案：
1. **第一阶段：** 基于标题的拆分，创建带标题元数据的章节级 chunk
2. **第二阶段：** 对每个章节进行递归大小拆分，强制执行 chunk_size / chunk_overlap

这是 LangChain 和 LlamaIndex 推荐的事实标准。

### 1.4 基于 AST 的解析

将 Markdown 解析为抽象语法树（AST），然后遍历树提取结构节点（标题、代码块、表格、列表、段落）。每种节点类型可以有不同的处理方式。

**关键优势：** 代码块、表格和列表作为原子单元处理——永远不会在元素中间拆分。

### 1.5 语义拆分（高级）

使用 embedding 模型计算连续句子之间的相似度，在语义转折点创建分割。LlamaIndex（`SemanticSplitterNodeParser`）和 LangChain（`SemanticChunker`）支持此功能。

**优点：** 产生语义最连贯的 chunk
**缺点：** 拆分时需要 embedding 模型；速度慢；成本高

---

## 2. Java/Spring Boot 库选型

### 2.1 commonmark-java（推荐 — 基于 AST 拆分）

干净、符合规范的 CommonMark 解析器。通过 `AbstractVisitor` 产生可遍历的 AST。轻量级（~100KB 核心），线程安全，无依赖。支持表格、标题锚点、删除线、自动链接、脚注等扩展。

**Maven 依赖：**
```xml
<dependency>
    <groupId>org.commonmark</groupId>
    <artifactId>commonmark</artifactId>
    <version>0.28.0</version>
</dependency>
<!-- 表格扩展 -->
<dependency>
    <groupId>org.commonmark</groupId>
    <artifactId>commonmark-ext-gfm-tables</artifactId>
    <version>0.28.0</version>
</dependency>
```

**示例：基于标题的拆分**
```java
import org.commonmark.node.*;
import org.commonmark.parser.Parser;
import java.util.*;

public class MarkdownHeaderSplitter {

    // 带标题层级元数据的 chunk
    public record MarkdownChunk(
        String content,
        Map<String, String> headingPath,  // 如 {H1: "介绍", H2: "安装"}
        int depth
    ) {}

    public List<MarkdownChunk> splitByHeaders(String markdown) {
        Parser parser = Parser.builder().build();
        Node document = parser.parse(markdown);

        List<MarkdownChunk> chunks = new ArrayList<>();
        Map<String, String> currentPath = new LinkedHashMap<>();
        StringBuilder currentContent = new StringBuilder();
        int currentDepth = 0;

        // 遍历 AST 节点
        for (Node node = document.getFirstChild(); node != null; node = node.getNext()) {
            if (node instanceof Heading heading) {
                // 刷出上一个章节
                if (currentContent.length() > 0) {
                    chunks.add(new MarkdownChunk(
                        currentContent.toString().trim(),
                        new LinkedHashMap<>(currentPath),
                        currentDepth
                    ));
                    currentContent.setLength(0);
                }
                // 提取标题文本
                String headingText = extractText(heading);
                int level = heading.getLevel();
                currentDepth = level;

                // 更新标题路径：覆盖当前级别，清除更深级别
                updateHeadingPath(currentPath, level, headingText);

                // 标题保留在内容中（strip_headers=False）
                currentContent.append("#".repeat(level))
                              .append(" ")
                              .append(headingText)
                              .append("\n\n");
            } else {
                // 非标题内容追加
                currentContent.append(renderToMarkdown(node)).append("\n");
            }
        }

        // 刷出最后一个章节
        if (currentContent.length() > 0) {
            chunks.add(new MarkdownChunk(
                currentContent.toString().trim(),
                new LinkedHashMap<>(currentPath),
                currentDepth
            ));
        }

        return chunks;
    }

    private void updateHeadingPath(Map<String, String> path, int level, String text) {
        // 清除所有更深级别
        path.keySet().removeIf(k -> Integer.parseInt(k.substring(1)) > level);
        path.put("H" + level, text);
    }

    private String extractText(Node node) {
        StringBuilder sb = new StringBuilder();
        Node child = node.getFirstChild();
        while (child != null) {
            if (child instanceof Text t) sb.append(t.getLiteral());
            else if (child instanceof Code c) sb.append(c.getLiteral());
            child = child.getNext();
        }
        return sb.toString();
    }

    private String renderToMarkdown(Node node) {
        // 生产环境使用 org.commonmark.renderer.markdown.MarkdownRenderer
        return node.toString(); // 简化示例
    }
}
```

**优缺点：**
- ✅ 轻量级，无依赖，线程安全，快速（比 pegdown 快 10-20 倍）
- ✅ 干净的 AST + 访问者模式；支持源位置追踪
- ❌ 无内置拆分器 — 需要自己编写遍历逻辑
- ❌ MarkdownRenderer 需要额外配置

### 2.2 flexmark-java（功能最丰富）

全功能 Markdown 解析器，30+ 扩展，每个 AST 节点都有源位置追踪，详细的树结构包括标题级别信息和 Section 分组。

**Maven 依赖：**
```xml
<dependency>
    <groupId>com.vladsch.flexmark</groupId>
    <artifactId>flexmark-all</artifactId>
    <version>0.64.8</version>
</dependency>
```

**优缺点：**
- ✅ 30+ 开箱即用的扩展；每个节点内置源位置追踪；Section 分组
- ✅ 详细的 AST，包含标题级别、Section 节点和丰富的访问者模式
- ❌ 依赖体积较大（~2-3MB）
- ❌ API 更复杂

### 2.3 LangChain4j（完整 RAG 管道）

Java LLM 框架，提供文档解析、拆分、embedding 和 RAG 编排。包含 `MarkdownDocumentParser` 和多种 `DocumentSplitter` 实现。

**Maven 依赖：**
```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j</artifactId>
    <version>1.0.0-beta1</version>
</dependency>
```

**优缺点：**
- ✅ 开箱即用的完整 RAG 管道；多种拆分策略；embedding 集成
- ❌ 如果只需要拆分功能则过于重量级；引入大量传递依赖
- ❌ 拆分内部控制不如自定义 AST 遍历灵活

### 2.4 对比矩阵

| 特性 | commonmark-java | flexmark-java | LangChain4j |
|------|-----------------|---------------|-------------|
| **体积（核心）** | ~100KB | ~2-3MB | ~10MB+ |
| **依赖** | 无（核心） | 模块化 | 大量传递依赖 |
| **AST 质量** | 干净，可遍历 | 丰富，Section 感知 | 不透明（内部） |
| **源位置追踪** | 通过 IncludeSourceSpans | 每个节点内置 | 不支持 |
| **线程安全** | 是（解析器可复用） | 是（解析器可复用） | 取决于实现 |
| **内置拆分器** | 无（需自建） | 无（需自建） | 有 |
| **扩展** | 表格、标题等 | 30+ 扩展 | 完整 LLM 栈 |
| **Java 版本** | 11+ | 8+ | 17+ |
| **适用场景** | 轻量级 AST 拆分 | 丰富 Markdown 处理 | 完整 RAG 管道 |

---

## 3. RAG 文档拆分最佳实践

### 3.1 Chunk 大小推荐

| 指标 | 推荐范围 | 说明 |
|------|----------|------|
| **Token 数** | 256-512 tokens | 大多数 RAG 应用的最佳区间 |
| **字符数** | 500-1000 字符 | 约等于 250-500 tokens |
| **最小值** | ~100 字符 / 50 tokens | 低于此值 chunk 缺乏上下文 |
| **最大值** | ~1500 tokens | 超过此值检索噪声增加 |
| **重叠** | chunk 大小的 10-20% | 如 500 字符 chunk 对应 50-100 字符重叠 |

对于 OpenAI embeddings，每个 chunk 200-400 tokens 效果最佳。

### 3.2 结构完整性规则

1. **永不拆分代码块。** 跟踪 ``` 开关状态；标记之间的所有内容作为一个元素。超出 maxSize 的代码块应保持完整——拆分会破坏语法并降低检索质量。

2. **永不拆分表格。** 检测以 `|` 开头/结尾的行，合并为单个表格元素。

3. **永不拆分列表。** 连续的列表行（以 `-`、`*`、`1.` 等开头）应作为一个块处理。

4. **优先在标题边界拆分。** 标题是 Markdown 中最强的语义边界。

5. **回退到段落边界**（`\n\n`）再回退到行边界（`\n`）。

### 3.3 元数据增强

每个 chunk 应携带：
- **完整标题层级**（如 `H1: API 参考 > H2: 认证 > H3: OAuth2`）
- **源文档**标识符
- **chunk 索引**（在文档中的位置）
- **块类型**（代码、表格、列表、段落）用于过滤检索

### 3.4 重叠策略

Chunk 重叠防止边界处的信息丢失：

```
Chunk N:   [===内容===]
Chunk N+1:        [===内容===]
                   ^^^ 重叠 ^^^
```

- 句子感知重叠：包含前一个 chunk 的最后 1-2 个句子
- 字符重叠：chunk_size 的 10-20%
- 代码块和表格不重叠（它们是原子的）

### 3.5 父子检索（重叠的替代方案）

使用两级索引代替重叠：
- **小 chunk**（200-300 tokens）用于 embedding 和匹配
- **大父 chunk**（1000-1500 tokens）用于上下文检索

当小 chunk 匹配时，返回其父 chunk 作为上下文。兼顾小 chunk 的精确度和大 chunk 的上下文。

---

## 4. DevKnowledge 项目实施方案

### 4.1 现状分析

当前 `KbService.java` 使用自定义状态机解析器：
- `BlockType` 枚举：CODE, HEADING, LIST, TABLE, PARAGRAPH
- `extractMarkdownBlocks()`：状态机解析 Markdown 为类型化块
- `adjustBlockSize()`：合并小块（< minSize=100）和拆分大块（> maxSize=1000）
- `splitLongParagraph()`：在句子边界拆分，最小 500 字符

**已识别的问题：**
1. 无 chunk 重叠机制
2. chunk 无完整标题层级元数据
3. 某些文档只产生 ~2 个 chunk（参考 `进度.md` 中的记录）
4. 超大表格和列表按行拆分（破坏结构）

### 4.2 推荐方案：使用 commonmark-java 的自定义 AST 拆分器

**理由：**
- 项目已有可用的状态机；升级为基于 AST 的解析可在不增加重量级依赖的情况下提高正确性
- commonmark-java 轻量级（~100KB），零传递依赖，线程安全——适合 WebFlux
- 项目已有自己的 embedding 和检索基础设施，不需要 LangChain4j 的完整 RAG 管道
- flexmark-java 更重，除非需要每个节点的源位置追踪，否则不必要

### 4.3 两阶段管道架构

```
Markdown 输入
     |
     v
[第一阶段：基于 AST 的标题拆分]
  - 使用 commonmark-java 解析
  - 遍历 AST，在 Heading 节点处拆分
  - 附带完整标题层级元数据
  - 代码块、表格、列表作为原子单元保留
     |
     v
[第二阶段：大小调整 + 重叠]
  - 合并小章节（< minChunkSize）
  - 在段落/句子边界拆分大章节（> maxChunkSize）
  - 应用 chunk 重叠（10-20%）
  - 不拆分原子块（代码、表格）
     |
     v
带元数据的 Chunks -> Embedding -> pgvector
```

### 4.4 实施计划

**第一步：添加 commonmark-java 依赖**

```xml
<!-- backend/pom.xml -->
<dependency>
    <groupId>org.commonmark</groupId>
    <artifactId>commonmark</artifactId>
    <version>0.28.0</version>
</dependency>
<dependency>
    <groupId>org.commonmark</groupId>
    <artifactId>commonmark-ext-gfm-tables</artifactId>
    <version>0.28.0</version>
</dependency>
```

**第二步：创建 `MarkdownChunkerService`**

用基于 AST 的实现替换 `KbService.java` 中的自定义状态机：

```java
@Service
public class MarkdownChunkerService {

    private static final int DEFAULT_MIN_CHUNK_SIZE = 100;
    private static final int DEFAULT_MAX_CHUNK_SIZE = 1000;
    private static final double DEFAULT_OVERLAP_RATIO = 0.15;  // 15% 重叠

    private final Parser mdParser;

    public MarkdownChunkerService() {
        // 线程安全：解析器可跨请求复用
        this.mdParser = Parser.builder()
            .extensions(List.of(TablesExtension.create()))
            .build();
    }

    /**
     * 两阶段 Markdown 拆分：
     * 1. 基于 AST 的标题拆分 + 元数据
     * 2. 大小调整 + 重叠
     */
    public List<MarkdownChunk> chunk(String markdown, String sourceId) {
        // 第一阶段：按标题拆分
        List<Section> sections = splitByHeaders(markdown);

        // 第二阶段：大小调整 + 重叠
        return adjustChunkSizes(sections, sourceId);
    }

    /**
     * 第一阶段：遍历 AST，在 Heading 节点处拆分，保留原子块。
     */
    private List<Section> splitByHeaders(String markdown) {
        Node document = mdParser.parse(markdown);
        List<Section> sections = new ArrayList<>();

        Map<String, String> headingPath = new LinkedHashMap<>();
        StringBuilder content = new StringBuilder();

        for (Node node = document.getFirstChild(); node != null; node = node.getNext()) {
            if (node instanceof Heading heading) {
                // 刷出上一个章节
                if (content.length() > 0) {
                    sections.add(new Section(
                        content.toString().trim(),
                        new LinkedHashMap<>(headingPath)
                    ));
                    content.setLength(0);
                }
                // 更新标题路径
                String text = extractText(heading);
                updatePath(headingPath, heading.getLevel(), text);

                // 标题保留在内容中（strip_headers=False）
                content.append("#".repeat(heading.getLevel()))
                       .append(" ").append(text).append("\n\n");
            } else {
                // 将节点渲染回 Markdown（保留代码块、表格等）
                content.append(renderNode(node)).append("\n");
            }
        }
        // 刷出最后一个章节
        if (content.length() > 0) {
            sections.add(new Section(content.toString().trim(), headingPath));
        }

        return sections;
    }

    /**
     * 第二阶段：合并小章节、拆分大章节、添加重叠。
     */
    private List<MarkdownChunk> adjustChunkSizes(List<Section> sections, String sourceId) {
        List<MarkdownChunk> chunks = new ArrayList<>();
        int chunkIndex = 0;

        for (Section section : sections) {
            if (section.content.length() <= DEFAULT_MAX_CHUNK_SIZE) {
                // 章节适合单个 chunk
                chunks.add(new MarkdownChunk(
                    section.content, section.headingPath, sourceId, chunkIndex++
                ));
            } else {
                // 在段落边界拆分大章节
                List<String> subChunks = splitAtParagraphs(
                    section.content, DEFAULT_MAX_CHUNK_SIZE
                );
                // 应用重叠
                List<String> overlapped = applyOverlap(subChunks, DEFAULT_OVERLAP_RATIO);
                for (String sub : overlapped) {
                    chunks.add(new MarkdownChunk(
                        sub, section.headingPath, sourceId, chunkIndex++
                    ));
                }
            }
        }

        // 合并相邻的小 chunk
        return mergeSmallChunks(chunks, DEFAULT_MIN_CHUNK_SIZE);
    }

    // ... renderNode、splitAtParagraphs、applyOverlap 等辅助方法

    public record MarkdownChunk(
        String content,
        Map<String, String> headingPath,
        String sourceId,
        int chunkIndex
    ) {}

    private record Section(String content, Map<String, String> headingPath) {}
}
```

**第三步：集成到现有 `KbService`**

将 `KbService` 中的 `extractMarkdownBlocks()` / `adjustBlockSize()` 调用替换为新的 `MarkdownChunkerService`。RAG 管道的其余部分（embedding、存储、检索）保持不变。

**第四步：可选 — 添加标题元数据列**

```sql
-- V11__add_chunk_heading_metadata.sql
ALTER TABLE kb_chunks ADD COLUMN IF NOT EXISTS heading_path JSONB;
CREATE INDEX IF NOT EXISTS idx_kb_chunks_heading_path ON kb_chunks USING GIN (heading_path);
```

### 4.5 迁移路径

现有的 `KbService` 状态机可保留作为非 Markdown 内容的回退方案。新的 `MarkdownChunkerService` 专门处理 Markdown。检测逻辑 `isMarkdownContent()` 保持不变。

```
内容输入
     |
     v
isMarkdownContent()?
  |           |
  是          否
  |           |
  v           v
MarkdownChunkerService      现有纯文本拆分器
(commonmark-java AST)       (基于行/句子)
  |           |
  v           v
  统一输出格式 -> Embedding -> pgvector
```

### 4.6 关键设计决策

| 决策 | 选择 | 理由 |
|------|------|------|
| **解析库** | commonmark-java | 轻量级，线程安全，零依赖，干净的 AST |
| **拆分策略** | 两阶段（标题 + 大小） | 行业标准；保持语义 + 强制大小约束 |
| **原子块** | 代码、表格、列表永不拆分 | 防止语义破坏；匹配当前行为 |
| **重叠** | chunk_size 的 15%，句子感知 | 跨 chunk 边界桥接上下文 |
| **标题元数据** | 完整层级存为 JSONB | 支持按章节路径过滤检索 |
| **集成方式** | 新 Service，不嵌入 KbService | 关注点分离；可独立测试 |

---

## 附录：Maven 依赖汇总

```xml
<!-- Markdown AST 解析（推荐） -->
<dependency>
    <groupId>org.commonmark</groupId>
    <artifactId>commonmark</artifactId>
    <version>0.28.0</version>
</dependency>
<dependency>
    <groupId>org.commonmark</groupId>
    <artifactId>commonmark-ext-gfm-tables</artifactId>
    <version>0.28.0</version>
</dependency>

<!-- 备选：全功能解析器（如需要） -->
<!--
<dependency>
    <groupId>com.vladsch.flexmark</groupId>
    <artifactId>flexmark-all</artifactId>
    <version>0.64.8</version>
</dependency>
-->

<!-- 完整 RAG 管道（如从零构建） -->
<!--
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j</artifactId>
    <version>1.0.0-beta1</version>
</dependency>
-->
```
