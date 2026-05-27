# 段落切分优化 + ReAct 推理结束条件优化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 优化知识库文档切分逻辑（Markdown 结构感知）和 ReAct Agent 推理结束条件（Prompt 引导 + 输出内容检测）

**Architecture:** 两个独立优化点。Task 1 在 KbService 中实现两遍处理的 Markdown 感知切分，替换现有简单 `\n\n` 切分。Task 2 在 ReActAgent 中新增完成信号检测，在 DemoService 的 system prompt 中追加推理控制指令。

**Tech Stack:** Java 17, Spring Boot 3.3 WebFlux, MyBatis Plus 3.5.7

---

## 文件结构

| 文件 | 操作 | 职责 |
|------|------|------|
| `backend/src/main/java/com/devknowledge/service/KbService.java` | 修改 | 重构 `splitIntoChunks()`，新增 `isMarkdownContent()`、`extractMarkdownBlocks()`、`adjustBlockSize()` |
| `backend/src/main/java/com/devknowledge/controller/KbController.java` | 修改 | 上传接口新增 `minChunkSize` / `maxChunkSize` 可选参数 |
| `backend/src/main/java/com/devknowledge/service/DemoService.java` | 修改 | `buildSystemPrompt()` 追加推理控制规则 |
| `backend/src/main/java/com/devknowledge/service/ai/ReActAgent.java` | 修改 | 新增完成信号检测逻辑、文本长度追踪 |

---

### Task 1: Markdown 感知段落切分（KbService）

**Files:**
- Modify: `backend/src/main/java/com/devknowledge/service/KbService.java`

- [ ] **Step 1: 添加 `isMarkdownContent()` 检测方法**

在 `KbService.java` 的 `splitIntoChunks()` 方法之前（约第 255 行），添加：

```java
/**
 * 判断内容是否为 Markdown 格式
 * 检测常见 Markdown 语法标记：标题、代码块、列表、表格
 */
private boolean isMarkdownContent(String content) {
    // 检测代码块
    if (content.contains("```")) return true;
    // 检测标题行（# 开头）
    if (content.matches("(?m)^#{1,6}\\s+.*")) return true;
    // 检测表格行（| 分隔）
    if (content.matches("(?m)^\\|.*\\|$")) return true;
    // 检测列表项（- 或 * 开头，或数字列表）
    if (content.matches("(?m)^\\s*[-*+]\\s+.*")) return true;
    if (content.matches("(?m)^\\s*\\d+\\.\\s+.*")) return true;
    return false;
}
```

- [ ] **Step 2: 添加 `extractMarkdownBlocks()` 结构提取方法**

在 `isMarkdownContent()` 之后添加。使用状态机解析 Markdown，将内容拆分为结构块：

```java
/**
 * 结构块类型
 */
private enum BlockType { CODE, HEADING, LIST, TABLE, PARAGRAPH }

/**
 * 结构块
 */
private static class MarkdownBlock {
    final BlockType type;
    final String content;
    final String headingPrefix; // 标题块的标题文本，用于子 chunk 前缀

    MarkdownBlock(BlockType type, String content, String headingPrefix) {
        this.type = type;
        this.content = content;
        this.headingPrefix = headingPrefix;
    }
}

/**
 * 提取 Markdown 结构块
 * 第一遍：按结构边界切分，代码块保持完整
 */
private List<MarkdownBlock> extractMarkdownBlocks(String content) {
    List<MarkdownBlock> blocks = new ArrayList<>();
    String[] lines = content.split("\n");
    StringBuilder buffer = new StringBuilder();
    BlockType currentType = BlockType.PARAGRAPH;
    String currentHeading = null;
    boolean inCodeBlock = false;

    for (String line : lines) {
        // 代码块围栏处理
        if (line.trim().startsWith("```")) {
            if (inCodeBlock) {
                // 代码块结束
                buffer.append(line).append("\n");
                blocks.add(new MarkdownBlock(BlockType.CODE, buffer.toString().trim(), null));
                buffer.setLength(0);
                inCodeBlock = false;
                currentType = BlockType.PARAGRAPH;
            } else {
                // 代码块开始 — 先保存之前的缓冲内容
                if (buffer.length() > 0) {
                    blocks.add(new MarkdownBlock(currentType, buffer.toString().trim(), currentHeading));
                    buffer.setLength(0);
                }
                inCodeBlock = true;
                currentType = BlockType.CODE;
                buffer.append(line).append("\n");
            }
            continue;
        }

        // 代码块内部：直接追加
        if (inCodeBlock) {
            buffer.append(line).append("\n");
            continue;
        }

        // 标题行检测
        if (line.matches("^#{1,6}\\s+.*")) {
            // 保存之前的缓冲内容
            if (buffer.length() > 0) {
                blocks.add(new MarkdownBlock(currentType, buffer.toString().trim(), currentHeading));
                buffer.setLength(0);
            }
            currentHeading = line.trim();
            currentType = BlockType.HEADING;
            buffer.append(line).append("\n");
            continue;
        }

        // 表格行检测（| 开头）
        boolean isTableLine = line.trim().startsWith("|") && line.trim().endsWith("|");
        // 列表项检测
        boolean isListLine = line.trim().matches("^[-*+]\\s+.*") || line.trim().matches("^\\d+\\.\\s+.*");

        BlockType lineType = isTableLine ? BlockType.TABLE : (isListLine ? BlockType.LIST : BlockType.PARAGRAPH);

        // 类型切换时保存缓冲
        if (lineType != currentType && buffer.length() > 0) {
            blocks.add(new MarkdownBlock(currentType, buffer.toString().trim(), currentHeading));
            buffer.setLength(0);
            // 标题前缀对后续非标题块不生效（新段落）
            if (currentType == BlockType.HEADING && lineType == BlockType.PARAGRAPH) {
                currentHeading = null;
            }
        }
        currentType = lineType;
        buffer.append(line).append("\n");
    }

    // 保存剩余缓冲
    if (buffer.length() > 0) {
        blocks.add(new MarkdownBlock(currentType, buffer.toString().trim(), currentHeading));
    }

    return blocks;
}
```

- [ ] **Step 3: 添加 `adjustBlockSize()` 尺寸调整方法**

在 `extractMarkdownBlocks()` 之后添加：

```java
/**
 * 第二遍：对结构块应用尺寸调整
 * - 代码块：不拆分，保持完整
 * - 段落/列表/表格块：合并过小的，拆分过大的
 */
private List<String> adjustBlockSize(List<MarkdownBlock> blocks, int minSize, int maxSize) {
    List<String> chunks = new ArrayList<>();
    StringBuilder buffer = new StringBuilder();

    for (MarkdownBlock block : blocks) {
        String content = block.content;
        if (content.isEmpty()) continue;

        // 代码块：不拆分，直接输出
        if (block.type == BlockType.CODE) {
            // 先 flush 缓冲
            if (buffer.length() > 0) {
                chunks.add(buffer.toString().trim());
                buffer.setLength(0);
            }
            chunks.add(content);
            continue;
        }

        // 标题块 + 内容超大：按子段落拆分，每个子 chunk 带标题前缀
        if (block.type == BlockType.HEADING && content.length() > maxSize) {
            if (buffer.length() > 0) {
                chunks.add(buffer.toString().trim());
                buffer.setLength(0);
            }
            String heading = block.headingPrefix != null ? block.headingPrefix : "";
            String body = content.substring(heading.length()).trim();
            // 按空行拆分子段落
            String[] subParts = body.split("\\n\\n+");
            StringBuilder subBuffer = new StringBuilder();
            for (String sub : subParts) {
                String trimmed = sub.trim();
                if (trimmed.isEmpty()) continue;
                if (subBuffer.length() + trimmed.length() < maxSize) {
                    if (subBuffer.length() > 0) subBuffer.append("\n\n");
                    subBuffer.append(trimmed);
                } else {
                    if (subBuffer.length() > 0) {
                        chunks.add(heading + "\n\n" + subBuffer.toString().trim());
                        subBuffer.setLength(0);
                    }
                    if (trimmed.length() > maxSize) {
                        // 超长段落用 splitLongParagraph 拆分
                        for (String lp : splitLongParagraph(trimmed)) {
                            chunks.add(heading + "\n\n" + lp);
                        }
                    } else {
                        subBuffer.append(trimmed);
                    }
                }
            }
            if (subBuffer.length() > 0) {
                chunks.add(heading + "\n\n" + subBuffer.toString().trim());
            }
            continue;
        }

        // 普通块：合并过小 / 拆分过大
        if (buffer.length() + content.length() < minSize) {
            if (buffer.length() > 0) buffer.append("\n\n");
            buffer.append(content);
        } else {
            if (buffer.length() > 0) {
                chunks.add(buffer.toString().trim());
                buffer.setLength(0);
            }
            if (content.length() > maxSize) {
                for (String sub : splitLongParagraph(content)) {
                    chunks.add(sub);
                }
            } else {
                chunks.add(content);
            }
        }
    }

    if (buffer.length() > 0) {
        chunks.add(buffer.toString().trim());
    }
    return chunks;
}
```

- [ ] **Step 4: 重构 `splitIntoChunks()` 调用新逻辑**

替换现有 `splitIntoChunks()` 方法（第 257-289 行），保留 `splitLongParagraph()` 不变：

```java
private static final int DEFAULT_MIN_CHUNK_SIZE = 100;
private static final int DEFAULT_MAX_CHUNK_SIZE = 1000;

/**
 * 智能段落切分
 * Markdown 内容：结构感知切分（代码块完整保留、标题+内容不分离）
 * 纯文本内容：按空行切分 + 尺寸调整
 */
private List<String> splitIntoChunks(String content, Integer minChunkSize, Integer maxChunkSize) {
    if (content == null || content.isBlank()) return List.of();

    int minSize = minChunkSize != null ? minChunkSize : DEFAULT_MIN_CHUNK_SIZE;
    int maxSize = maxChunkSize != null ? maxChunkSize : DEFAULT_MAX_CHUNK_SIZE;
    // 参数校验：min < max，min > 0
    minSize = Math.max(20, minSize);
    maxSize = Math.max(minSize + 50, maxSize);

    if (isMarkdownContent(content)) {
        // Markdown 模式：结构感知切分
        List<MarkdownBlock> blocks = extractMarkdownBlocks(content);
        return adjustBlockSize(blocks, minSize, maxSize);
    } else {
        // 纯文本模式：保留原有逻辑，参数化
        return splitPlainText(content, minSize, maxSize);
    }
}

/**
 * 纯文本切分（原 splitIntoChunks 逻辑，参数化）
 */
private List<String> splitPlainText(String content, int minSize, int maxSize) {
    String[] rawParts = content.split("\\n\\n+");
    List<String> chunks = new ArrayList<>();
    StringBuilder buffer = new StringBuilder();

    for (String part : rawParts) {
        String trimmed = part.trim();
        if (trimmed.isEmpty()) continue;

        if (buffer.length() + trimmed.length() < minSize) {
            if (buffer.length() > 0) buffer.append("\n\n");
            buffer.append(trimmed);
        } else {
            if (buffer.length() > 0) {
                chunks.add(buffer.toString());
                buffer.setLength(0);
            }
            if (trimmed.length() > maxSize) {
                for (String sub : splitLongParagraph(trimmed)) {
                    chunks.add(sub);
                }
            } else {
                chunks.add(trimmed);
            }
        }
    }
    if (buffer.length() > 0) {
        chunks.add(buffer.toString());
    }
    return chunks;
}
```

- [ ] **Step 5: 更新 `chunkAndEmbed()` 调用签名**

在 `KbService.java` 的 `chunkAndEmbed()` 方法（第 200 行），将调用从 `splitIntoChunks(content)` 改为 `splitIntoChunks(content, null, null)`（使用默认值）。同时修改方法签名以接受 chunk size 参数：

```java
private void chunkAndEmbed(UUID kbId, UUID docId, String content, Integer minChunkSize, Integer maxChunkSize) {
    // ... 现有逻辑不变 ...
    List<String> chunks = splitIntoChunks(content, minChunkSize, maxChunkSize);
    // ... 后续不变 ...
}
```

- [ ] **Step 6: 更新 `uploadDocument()` 签名**

在 `KbService.java` 的 `uploadDocument()` 方法（第 99 行），添加 chunk size 参数并传递到 `chunkAndEmbed()`：

```java
public Mono<KbDocument> uploadDocument(UUID kbId, String filename, long fileSize, byte[] content,
                                        Integer minChunkSize, Integer maxChunkSize) {
    // ... 现有逻辑不变 ...
    // 将 parseExecutor.submit 中的调用改为：
    chunkAndEmbed(kbId, doc.getId(), doc.getContent(), minChunkSize, maxChunkSize);
    // ...
}
```

- [ ] **Step 7: 更新 `KbController` 上传接口**

在 `KbController.java` 的两个上传接口中添加 `@RequestParam`：

单文件上传（第 63-80 行）：
```java
@PostMapping(value = "/{id}/documents", consumes = "multipart/form-data")
public Mono<ResponseEntity<KbDocument>> uploadDocument(
        @RequestHeader("Authorization") String authHeader,
        @PathVariable UUID id,
        @RequestPart("file") Mono<FilePart> file,
        @RequestParam(required = false) Integer minChunkSize,
        @RequestParam(required = false) Integer maxChunkSize) {
    // ... 现有逻辑不变 ...
    // 将调用改为：
    kbService.uploadDocument(id, fp.filename(), bytes.length, bytes, minChunkSize, maxChunkSize)
    // ...
}
```

批量上传（第 82-100 行）：
```java
@PostMapping(value = "/{id}/documents/batch", consumes = "multipart/form-data")
public Mono<ResponseEntity<List<KbDocument>>> batchUpload(
        @RequestHeader("Authorization") String authHeader,
        @PathVariable UUID id,
        @RequestPart("files") Flux<FilePart> files,
        @RequestParam(required = false) Integer minChunkSize,
        @RequestParam(required = false) Integer maxChunkSize) {
    // ... 现有逻辑不变 ...
    // 将调用改为：
    kbService.uploadDocument(id, fp.filename(), bytes.length, bytes, minChunkSize, maxChunkSize)
    // ...
}
```

- [ ] **Step 8: 编译验证**

Run: `cd backend && mvn compile`
Expected: BUILD SUCCESS

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/com/devknowledge/service/KbService.java \
       backend/src/main/java/com/devknowledge/controller/KbController.java
git commit -m "feat(kb): Markdown 结构感知段落切分，支持自定义 chunk size 参数"
```

---

### Task 2: ReAct 推理结束条件优化

**Files:**
- Modify: `backend/src/main/java/com/devknowledge/service/DemoService.java:324-357`
- Modify: `backend/src/main/java/com/devknowledge/service/ai/ReActAgent.java`

- [ ] **Step 1: 在 `buildSystemPrompt()` 追加推理控制规则**

在 `DemoService.java` 的 `buildSystemPrompt()` 方法末尾（第 355 行 `return prompt.toString()` 之前），追加：

```java
prompt.append("\n推理控制规则：\n");
prompt.append("- 当你已收集到足够信息来回答用户问题时，直接输出完整回答，不要再调用工具\n");
prompt.append("- 如果连续两次工具调用都没有获取到有用信息，请基于已有知识直接回答\n");
prompt.append("- 不要重复调用相同的工具或搜索相同的关键词\n");
```

- [ ] **Step 2: 在 ReActAgent 添加完成信号正则常量**

在 `ReActAgent.java` 类的常量区域（第 23 行之后）添加：

```java
import java.util.regex.Pattern;

// ... existing constants ...
/** 完成信号关键词正则（中文场景） */
private static final Pattern COMPLETION_PATTERN = Pattern.compile(
        "以下是最终答案|最终回答|总结如下|以下是完整的|以上就是|综上所述|代码如下");
/** 完成信号最小文本长度 */
private static final int MIN_COMPLETION_TEXT_LENGTH = 100;
```

- [ ] **Step 3: 在 `runRound()` 添加文本输出长度追踪**

在 `runRound()` 方法中，紧跟 `AtomicBoolean hasToolCall` 声明之后（第 83 行），添加文本长度追踪器：

```java
AtomicInteger textOutputLength = new AtomicInteger(0);
```

在 `doOnNext` 的 TEXT 分支中（第 91-93 行），更新长度追踪：

```java
} else if (chunk.getType() == AiChunkType.TEXT) {
    // 追踪文本输出总长度
    if (chunk.getContent() != null) {
        textOutputLength.addAndGet(chunk.getContent().length());
    }
    sink.tryEmitNext(chunk);
}
```

- [ ] **Step 4: 在 `doOnComplete` 中实现完成信号检测**

在 `doOnComplete` 回调中（第 101 行之后），**在现有的 `!hasToolCall.get()` 检查之前**，插入完成信号检测逻辑：

```java
.doOnComplete(() -> {
    log.info("ReAct 第 {} 轮完成，hasToolCall={}，textLength={}",
            currentRound + 1, hasToolCall.get(), textOutputLength.get());

    // 完成信号检测：后半程 + 无工具调用 + 足够文本 + 包含完成关键词
    if (!hasToolCall.get()
            && currentRound + 1 >= maxIterations / 2
            && textOutputLength.get() >= MIN_COMPLETION_TEXT_LENGTH) {
        // 检查本轮文本是否包含完成信号
        String roundText = messages.stream()
                .filter(m -> "assistant".equals(m.role()))
                .map(AiProviderAdapter.ChatMessage::content)
                .reduce("", (a, b) -> a + " " + b);
        if (COMPLETION_PATTERN.matcher(roundText).find()) {
            log.info("检测到完成信号关键词，结束推理");
            sink.tryEmitNext(AiChunk.done());
            sink.tryEmitComplete();
            return;
        }
    }

    // 模型没有调用工具 → 正常结束（原有逻辑）
    if (!hasToolCall.get()) {
        sink.tryEmitNext(AiChunk.done());
        sink.tryEmitComplete();
        return;
    }

    // ... 后续的 maxIterations / 死循环 / 全失败检测保持不变 ...
```

- [ ] **Step 5: 编译验证**

Run: `cd backend && mvn compile`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/devknowledge/service/DemoService.java \
       backend/src/main/java/com/devknowledge/service/ai/ReActAgent.java
git commit -m "feat(agent): ReAct 推理结束条件优化 — prompt 引导 + 完成信号检测"
```

---

### Task 3: 移除 TODO 注释

**Files:**
- Modify: `backend/src/main/java/com/devknowledge/service/KbService.java:256`
- Modify: `backend/src/main/java/com/devknowledge/service/ai/ReActAgent.java:103`

- [ ] **Step 1: 移除已解决的 TODO 注释**

KbService.java 第 256 行，删除：
```java
//TODO: 段落切分逻辑需要优化，目前是简单地按空行切分，但这样会把段落中间的换行也切分开了
```

ReActAgent.java 第 103 行，删除：
```java
//Todo: 满足结束推理的条件 除了工具调用，还需要判断模型是否还有未完成的任务或者有其他操作
```

- [ ] **Step 2: 编译验证**

Run: `cd backend && mvn compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/devknowledge/service/KbService.java \
       backend/src/main/java/com/devknowledge/service/ai/ReActAgent.java
git commit -m "chore: 移除已解决的 TODO 注释"
```
