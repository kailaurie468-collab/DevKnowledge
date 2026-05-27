# 段落切分优化 + ReAct 推理结束条件优化

> 日期：2026-05-27
> 状态：已批准

## 背景

代码中存在两个待优化 TODO：

1. `KbService.java:256` — 段落切分逻辑需要优化，目前按 `\n\n` 切分会破坏 Markdown 结构
2. `ReActAgent.java:103` — 推理结束条件不完善，仅靠"无工具调用"判断结束

---

## 一、段落切分优化（KbService）

### 问题

当前 `splitIntoChunks()` 用 `content.split("\\n\\n+")` 切分，对 Markdown 文档会导致：
- 代码块被拆散（``` 围栏中间的空行触发切分）
- 标题与正文内容分离
- 列表、表格被切断

### 方案：Markdown 结构感知切分

**两遍处理**：

**第一遍 — 结构提取**：用正则将内容拆分为结构块：
- 代码块（``` ... ```）→ 整块作为单个 chunk，不可拆分
- 标题 + 内容（`# xxx` 到下一个同级标题之间）→ 作为一个逻辑单元
- 连续列表/表格 → 作为一个逻辑单元
- 剩余的普通段落 → 标记为 `PARAGRAPH` 类型

**第二遍 — 尺寸调整**：对每个结构块应用：
- 代码块：不拆分，保持完整（即使超过 maxChars）
- 段落块：合并过小的（< minChars），拆分过大的（> maxChars，按句子边界）
- 标题块：如果超大，按子段落拆分，每个子 chunk 带标题前缀

**参数**：
- 默认 `minChunkSize = 100`，`maxChunkSize = 1000`
- 前端上传文档时可通过查询参数 `minChunkSize` / `maxChunkSize` 自定义

### 改动范围

| 文件 | 改动 |
|------|------|
| `KbService.java` | 新增 `extractMarkdownBlocks()`、`isMarkdownContent()` 方法；重构 `splitIntoChunks()` 调用新逻辑；保留 `splitLongParagraph()` 作为段落级拆分兜底 |
| `KbController.java` | 上传接口新增 `@RequestParam(required = false) Integer minChunkSize / maxChunkSize`，传递到 `KbService.uploadDocument()` |
| `KbService.uploadDocument()` | 签名增加 chunk size 参数，传递到 `splitIntoChunks()` |

---

## 二、ReAct 推理结束条件优化（ReActAgent）

### 问题

当前终止条件：
1. 无工具调用 → 正常结束
2. 最大轮数 → 强制结束
3. 死循环检测 → 停止
4. 连续全失败 2 轮 → 停止

缺少对模型输出内容的智能判断，可能在模型还有未完成任务时提前结束，或在模型已给出完整答案时继续浪费 token。

### 方案：Prompt 引导 + 输出内容检测 + 现有兜底

#### 2.1 Prompt 引导（DemoService）

在 `buildSystemPrompt()` 末尾追加：

```
推理控制规则：
- 当你已收集到足够信息来回答用户问题时，直接输出完整回答，不要再调用工具
- 如果连续两次工具调用都没有获取到有用信息，请基于已有知识直接回答
- 不要重复调用相同的工具或搜索相同的关键词
```

#### 2.2 输出内容检测（ReActAgent）

在 `doOnComplete` 终止判断中，**在"无工具调用→正常结束"之前**新增完成信号检测：

**全部满足才判定为完成**：
1. 当前轮无工具调用
2. 当前轮次 >= `maxIterations / 2`（后半程才检测，避免过早终止）
3. 收集的文本输出长度 >= 100 字符
4. 文本中包含完成信号关键词

**完成信号关键词**（正则，中文场景）：
```java
Pattern.compile("以下是最终答案|最终回答|总结如下|以下是完整的|以上就是|综上所述|代码如下")
```

**兜底机制保留不变**：max iterations + 死循环检测 + 连续全失败检测

### 改动范围

| 文件 | 改动 |
|------|------|
| `DemoService.java` | `buildSystemPrompt()` 追加推理控制规则 |
| `ReActAgent.java` | `runRound()` 新增完成信号检测逻辑；新增文本输出长度追踪；新增 `COMPLETION_PATTERN` 正则常量 |

---

## 三、不动的部分

- `DemoService.java:97` RAG 检测指标 — 本次不处理
- 现有兜底机制（max iterations / 死循环 / 连续失败）— 保留不变
- 前端代码 — 无改动（除上传接口新增可选参数）
