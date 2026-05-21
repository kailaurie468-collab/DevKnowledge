# Demo Tags Jieba 分词优化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用 Jieba TF-IDF 替换现有硬编码关键词提取，提升中文分词质量，并让 tags 参与 Demo 搜索过滤。

**Architecture:** 引入 `jieba-analysis` Java 库，替换 `DemoService.extractKeywords` 为 TF-IDF 关键词提取（只分析 prompt + title），在 `getUserDemos` 搜索条件中追加 tags 字段匹配。

**Tech Stack:** Java 17, Spring Boot 3.3, jieba-analysis 1.0.2, MyBatis Plus 3.5.7

---

### Task 1: 引入 jieba-analysis 依赖

**Files:**
- Modify: `backend/pom.xml`

- [ ] **Step 1: 在 `<dependencies>` 中添加 jieba-analysis**

在 `backend/pom.xml` 的 `<dependencies>` 节点内（Lombok 依赖之后）添加：

```xml
<dependency>
    <groupId>com.huaban</groupId>
    <artifactId>jieba-analysis</artifactId>
    <version>1.0.2</version>
</dependency>
```

- [ ] **Step 2: 下载依赖确认可用**

Run: `cd backend && mvn dependency:resolve -q`
Expected: BUILD SUCCESS，无报错

- [ ] **Step 3: Commit**

```bash
git add backend/pom.xml
git commit -m "deps: add jieba-analysis for keyword extraction"
```

---

### Task 2: 替换 extractKeywords 为 Jieba TF-IDF

**Files:**
- Modify: `backend/src/main/java/com/devknowledge/service/DemoService.java`

- [ ] **Step 1: 添加 Jieba 相关 import**

在 `DemoService.java` 文件顶部 import 区域添加：

```java
import com.huaban.jieba.analysis.TFIDFExtractor;
import com.huaban.jieba.analysis.Keyword;
```

- [ ] **Step 2: 替换 extractKeywords 方法**

找到现有的 `extractKeywords` 方法（约第 222-244 行），替换整个方法：

```java
/**
 * 使用 Jieba TF-IDF 从 prompt 和 title 中提取关键词
 * 只分析 prompt + title（AI 生成的摘要），不分析 output（大部分是代码）
 */
private String[] extractKeywords(String prompt, String title) {
    String text = prompt + " " + (title != null ? title : "");
    try {
        TFIDFExtractor extractor = new TFIDFExtractor();
        List<Keyword> keywords = extractor.extract(text, 8);
        return keywords.stream()
                .map(Keyword::getName)
                .filter(k -> k.length() >= 2)
                .toArray(String[]::new);
    } catch (Exception e) {
        log.warn("Jieba 关键词提取失败，回退到简单分割: {}", e.getMessage());
        // 回退：按空格/标点分割
        Set<String> fallback = new LinkedHashSet<>();
        for (String word : text.split("[\\s,，。、;；]+")) {
            if (word.length() >= 2 && word.length() <= 20) {
                fallback.add(word);
            }
        }
        return fallback.stream().limit(8).toArray(String[]::new);
    }
}
```

- [ ] **Step 3: 修改 saveDemoSync 中的调用处**

找到 `saveDemoSync` 方法中的这行（约第 180 行）：

```java
String[] tags = extractKeywords(req.getPrompt(), output);
```

替换为：

```java
String[] tags = extractKeywords(req.getPrompt(), demo.getTitle());
```

**注意**：这行必须在 `demo.setTitle(generateTitle(req.getPrompt()))` 之后。查看当前代码，`setTitle` 在第 185 行，`extractKeywords` 在第 180 行。需要把 `setTitle` 提到 `extractKeywords` 之前。

调整 `saveDemoSync` 中的顺序：

```java
private void saveDemoSync(UUID userId, GenerateDemoRequest req, String output) {
    try {
        String codeContent = extractCodeBlocks(output);
        String title = generateTitle(req.getPrompt());

        Demo demo = new Demo();
        demo.setId(UUID.randomUUID());
        demo.setUserId(userId);
        demo.setTitle(title);
        demo.setPrompt(req.getPrompt());
        demo.setFrameworkId(req.getFrameworkId());
        demo.setCodeContent(codeContent.isEmpty() ? output.substring(0, Math.min(output.length(), 500)) : codeContent);
        demo.setExplanation("");
        demo.setLanguage(req.getLanguage() != null ? req.getLanguage() : "typescript");

        // 先设置 title，再提取关键词（依赖 title）
        String[] tags = extractKeywords(req.getPrompt(), title);
        demo.setTags(tags);

        demo.setTokensUsed(estimateTokens(output));
        demo.setCreatedAt(Instant.now());
        demoMapper.insert(demo);
        log.info("Demo 已保存: id={}, title={}, tags={}, tokens={}", demo.getId(), demo.getTitle(), String.join(",", tags), demo.getTokensUsed());
    } catch (Exception e) {
        log.error("保存 Demo 失败: {}", e.getMessage(), e);
    }
}
```

- [ ] **Step 4: 编译验证**

Run: `cd backend && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/devknowledge/service/DemoService.java
git commit -m "feat: replace extractKeywords with Jieba TF-IDF"
```

---

### Task 3: Demo 搜索增加 tags 匹配

**Files:**
- Modify: `backend/src/main/java/com/devknowledge/service/DemoService.java:275-281`

- [ ] **Step 1: 在 keyword 搜索条件中追加 tags 匹配**

找到 `getUserDemos` 方法中的搜索条件（约第 275-281 行）：

```java
if (keyword != null && !keyword.isBlank()) {
    wrapper.and(w -> w
            .like(Demo::getTitle, keyword)
            .or()
            .like(Demo::getPrompt, keyword)
            .or()
            .like(Demo::getLanguage, keyword));
}
```

替换为：

```java
if (keyword != null && !keyword.isBlank()) {
    wrapper.and(w -> w
            .like(Demo::getTitle, keyword)
            .or()
            .like(Demo::getPrompt, keyword)
            .or()
            .like(Demo::getLanguage, keyword)
            .or()
            .like(Demo::getTags, keyword));
}
```

- [ ] **Step 2: 编译验证**

Run: `cd backend && mvn compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/devknowledge/service/DemoService.java
git commit -m "feat: include tags in Demo keyword search"
```

---

### Task 4: 端到端验证

- [ ] **Step 1: 启动后端，生成一个 Demo**

Run: `cd backend && mvn spring-boot:run`
Expected: 应用启动成功

用 curl 或前端生成一个 Demo（如 "帮我写一个 React useEffect 的 hook"），检查日志中 tags 输出：

Expected 日志类似：`Demo 已保存: id=..., title=..., tags=useEffect,React,hook,...`

- [ ] **Step 2: 测试 tags 搜索**

在历史记录搜索框输入一个 tag 关键词（如 "React"），验证包含该 tag 的 Demo 出现在搜索结果中。

- [ ] **Step 3: 最终 Commit**

```bash
git add -A
git commit -m "feat: Demo tags Jieba 分词优化完成"
```
