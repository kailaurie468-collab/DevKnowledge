# Skills 模块增强 — 开发计划

> **版本**: 1.0
> **日期**: 2026-06-09
> **状态**: Draft
> **基于 PRD**: `docs/superpowers/specs/skills-module-prd.md`
> **基于架构**: `docs/superpowers/plans/skills-module-architecture.md`

---

## 1. 任务拆分

共 22 个任务，按依赖顺序排列。每个任务可在 1-4 小时内完成。

| # | 任务 | 优先级 | 预估 | 依赖 | 输入 | 输出 |
|---|------|--------|------|------|------|------|
| T01 | Flyway 迁移脚本 V14 | P0 | 1h | 无 | PRD 数据模型定义 | `V14__create_skills_tables.sql`，4 张表 + 索引创建成功 |
| T02 | Model 层 — Skill + SkillStep | P0 | 1h | T01 | 表结构定义 | `Skill.java`、`SkillStep.java` 实体类 |
| T03 | Model 层 — SkillSuggestion + UserActivity | P0 | 1h | T01 | 表结构定义 | `SkillSuggestion.java`、`UserActivity.java` 实体类 |
| T04 | Mapper 层 — 全部 4 个 Mapper | P0 | 1.5h | T02, T03 | Model 层完成 | `SkillMapper`、`SkillStepMapper`、`SkillSuggestionMapper`、`UserActivityMapper` |
| T05 | DTO 层 — 请求/响应对象 | P0 | 1h | 无 | API 设计文档 | `ExtractSkillRequest`、`SkillExportResponse`、`ActivityRequest`、`SkillUpdateRequest` |
| T06 | SkillStepService | P0 | 1.5h | T04 | Mapper 层完成 | Steps 的 CRUD + replaceSteps 事务方法 |
| T07 | SkillService — CRUD | P0 | 2h | T04, T06 | Mapper + StepService | Skill 增删改查 + 分页搜索 + Markdown 导出 |
| T08 | ActivityService | P0 | 1.5h | T04 | Mapper 层完成 | 行为记录写入（含 5 分钟去重）+ 查询 + 清理 |
| T09 | SkillExtractionService | P0 | 2.5h | T07 | SkillService + ReActAgent | AI 提取 + JSON 解析 + 自动保存 |
| T10 | SkillSuggestionService — 规则引擎 | P0 | 3h | T07, T08 | SkillService + ActivityService | 频率分析 + 模式识别 + 模板匹配 + 推荐 CRUD |
| T11 | SkillController | P0 | 2.5h | T07, T09, T10 | 三个 Service 完成 | `/api/skills/*` 全部端点 |
| T12 | ActivityController | P0 | 1h | T08 | ActivityService 完成 | `/api/activities/*` 端点 |
| T13 | 前端 — 行为数据采集接入 | P0 | 1.5h | T12 | ActivityController 完成 | `LocalActivityStorage.ts` 增加 `recordActivityHybrid`；DemoPage、KbPage 接入调用 |
| T14 | 前端 — SkillsPage 搜索与过滤 | P1 | 2h | T11 | SkillController 完成 | 搜索框 + 分类 chips + 框架下拉 + 实时过滤 |
| T15 | 前端 — Skill 在线编辑 | P1 | 2.5h | T11 | SkillController 完成 | 详情面板编辑模式 + 步骤增删改 + 自动保存 |
| T16 | 前端 — StorageSettings 增强 | P1 | 1.5h | T12 | ActivityController 完成 | 存储模式持久化 + 浏览器检测 + 同步状态 |
| T17 | 前端 — 推荐卡片编辑后采纳 | P1 | 1.5h | T11 | SkillController 完成 | SkillSuggestionCard 编辑模式完善 |
| T18 | SkillSuggestionService — AI 增强推荐 | P1 | 3h | T10, T09 | 规则引擎 + ExtractionService | LLM 驱动的个性化推荐 + sourceSummary 生成 |
| T19 | 前端 — Skill 步骤拖拽排序 | P2 | 2h | T15 | 在线编辑完成 | 步骤列表拖拽重排 + 自动更新 stepOrder |
| T20 | 前端 — 自定义分类管理 | P2 | 1.5h | T14 | 搜索过滤完成 | 自定义分类输入 + 分类管理面板 |
| T21 | 前端 — 本地数据迁移工具 | P2 | 2h | T13, T16 | 采集 + StorageSettings 完成 | 一键上传本地 JSONL 到服务端 |
| T22 | 前端 — 同步状态面板 | P2 | 1.5h | T16 | StorageSettings 增强完成 | 最后同步时间 + 数据量统计显示 |

---

## 2. 文件清单

### 2.1 后端新增文件

| 任务 | 文件路径 | 说明 |
|------|---------|------|
| T01 | `backend/src/main/resources/db/migration/V14__create_skills_tables.sql` | skills、skill_steps、skill_suggestions、user_activities 四张表 + 7 个索引 |
| T02 | `backend/src/main/java/com/devknowledge/model/Skill.java` | Skills 主表实体，@TableName("skills")，UUID 主键 |
| T02 | `backend/src/main/java/com/devknowledge/model/SkillStep.java` | Steps 实体，skillId 外键 + stepOrder 排序 |
| T03 | `backend/src/main/java/com/devknowledge/model/SkillSuggestion.java` | Suggestions 实体，suggestedSteps 用 JSONB (JacksonTypeHandler) |
| T03 | `backend/src/main/java/com/devknowledge/model/UserActivity.java` | Activities 实体，keywords 用 StringArrayTypeHandler (TEXT[]) |
| T04 | `backend/src/main/java/com/devknowledge/mapper/SkillMapper.java` | 继承 BaseMapper<Skill> |
| T04 | `backend/src/main/java/com/devknowledge/mapper/SkillStepMapper.java` | 继承 BaseMapper<SkillStep>，按 skillId + stepOrder 查询 |
| T04 | `backend/src/main/java/com/devknowledge/mapper/SkillSuggestionMapper.java` | 继承 BaseMapper<SkillSuggestion> |
| T04 | `backend/src/main/java/com/devknowledge/mapper/UserActivityMapper.java` | 继承 BaseMapper<UserActivity>，含 getTopFrameworks/getTopKeywords 自定义查询 |
| T05 | `backend/src/main/java/com/devknowledge/dto/ExtractSkillRequest.java` | description + frameworkId + category |
| T05 | `backend/src/main/java/com/devknowledge/dto/SkillExportResponse.java` | content 字段 |
| T05 | `backend/src/main/java/com/devknowledge/dto/ActivityRequest.java` | type + framework + keywords + language + resultCount + metadata |
| T05 | `backend/src/main/java/com/devknowledge/dto/SkillUpdateRequest.java` | name + description + category + triggerDescription + steps[] |
| T06 | `backend/src/main/java/com/devknowledge/service/SkillStepService.java` | Steps CRUD + replaceSteps 事务 |
| T07 | `backend/src/main/java/com/devknowledge/service/SkillService.java` | Skill CRUD + 分页搜索 + Markdown 导出 |
| T08 | `backend/src/main/java/com/devknowledge/service/ActivityService.java` | 行为记录 + 5 分钟去重 + 查询 + 清理 |
| T09 | `backend/src/main/java/com/devknowledge/service/SkillExtractionService.java` | ReActAgent 调用 + System Prompt 构造 + JSON 解析 + 自动保存 |
| T10 | `backend/src/main/java/com/devknowledge/service/SkillSuggestionService.java` | 规则引擎 + 推荐 CRUD + accept/dismiss |
| T11 | `backend/src/main/java/com/devknowledge/controller/SkillController.java` | Skills + Suggestions 全部 REST 端点 |
| T12 | `backend/src/main/java/com/devknowledge/controller/ActivityController.java` | Activities REST 端点 |

### 2.2 前端修改文件

| 任务 | 文件路径 | 修改内容 |
|------|---------|---------|
| T13 | `frontend/src/storage/LocalActivityStorage.ts` | 新增 `recordActivityHybrid()` 函数，根据存储模式决定写入目标（本地/服务端/双写） |
| T13 | `frontend/src/pages/DemoPage.tsx` | 生成完成回调中调用 `recordActivityHybrid({ type: 'demo_generate', ... })` |
| T13 | `frontend/src/pages/KbPage.tsx` | 搜索回调中调用 `recordActivityHybrid({ type: 'kb_search', ... })` |
| T14 | `frontend/src/pages/SkillsPage.tsx` | 增加搜索输入框、分类 chips 过滤、框架下拉过滤 |
| T15 | `frontend/src/pages/SkillsPage.tsx` | 详情面板增加编辑模式（复用 SkillSuggestionCard 编辑 UI） |
| T16 | `frontend/src/pages/settings/StorageSettings.tsx` | storageMode 持久化到 localStorage、非 Chromium 检测、同步状态显示 |
| T17 | `frontend/src/components/skills/SkillSuggestionCard.tsx` | 编辑模式完善，编辑后调用 updateSuggestion API |
| T19 | `frontend/src/pages/SkillsPage.tsx` | 步骤列表增加拖拽排序（可用 @dnd-kit 或原生 HTML5 DnD） |
| T20 | `frontend/src/pages/SkillsPage.tsx` | 自定义分类输入 + 分类管理面板 |
| T21 | `frontend/src/pages/settings/StorageSettings.tsx` | "上传本地数据到云端"按钮 + 迁移进度 |
| T22 | `frontend/src/pages/settings/StorageSettings.tsx` | 同步状态面板（最后同步时间、数据条数） |

---

## 3. 实现要点

### 3.1 T01 — Flyway 迁移脚本

```sql
-- V14__create_skills_tables.sql
-- 注意：Flyway 迁移文件一旦提交不可修改（校验和机制），务必一次写对

-- skills 表：user_id 引用 users(id)，framework_id 引用 frameworks(id)
-- skill_steps 表：skill_id 引用 skills(id) ON DELETE CASCADE
-- skill_suggestions 表：suggested_steps 用 JSONB 存储结构化步骤
-- user_activities 表：keywords 用 TEXT[] 数组类型

-- 关键：所有 UUID 列使用 DEFAULT gen_random_uuid()
-- 关键：所有时间列使用 TIMESTAMPTZ NOT NULL DEFAULT now()
```

**陷阱**：
- PostgreSQL 的 `TEXT[]` 数组类型在 MyBatis Plus 中需要自定义 TypeHandler（已有 `StringArrayTypeHandler`，直接复用）
- `JSONB` 列需要 `JacksonTypeHandler`，Model 上需加 `@TableField(typeHandler = JacksonTypeHandler.class)`
- `frameworks` 表的外键引用需确认表名和主键类型（UUID）

### 3.2 T06 — SkillStepService.replaceSteps 事务

```java
/**
 * 替换 Skill 的所有步骤（先删后插）
 * 必须在同一事务中执行，防止数据不一致
 */
@Transactional
public void replaceSteps(UUID skillId, List<SkillStep> newSteps) {
    // 1. 删除旧步骤
    stepMapper.delete(new LambdaQueryWrapper<SkillStep>()
        .eq(SkillStep::getSkillId, skillId));
    // 2. 插入新步骤（维护 stepOrder）
    for (int i = 0; i < newSteps.size(); i++) {
        SkillStep step = newSteps.get(i);
        step.setId(UUID.randomUUID());
        step.setSkillId(skillId);
        step.setStepOrder(i + 1);
        step.setCreatedAt(Instant.now());
        step.setUpdatedAt(Instant.now());
        stepMapper.insert(step);
    }
}
```

**注意**：WebFlux 环境下 `@Transactional` 需要确保在 `Schedulers.boundedElastic()` 线程中执行。`replaceSteps` 被 `SkillService.updateSkill` 调用时，外层已有 `subscribeOn(Schedulers.boundedElastic())`，事务注解可正常生效。

### 3.3 T07 — SkillService Markdown 导出

```java
public Mono<String> exportToMarkdown(UUID skillId, UUID userId) {
    return Mono.fromCallable(() -> {
        Skill skill = skillMapper.selectById(skillId);
        if (skill == null || !skill.getUserId().equals(userId))
            throw new RuntimeException("无权访问");

        List<SkillStep> steps = stepMapper.selectList(
            new LambdaQueryWrapper<SkillStep>()
                .eq(SkillStep::getSkillId, skillId)
                .orderByAsc(SkillStep::getStepOrder));

        StringBuilder md = new StringBuilder();
        md.append("# ").append(skill.getName()).append("\n\n");
        if (skill.getDescription() != null)
            md.append("**描述**: ").append(skill.getDescription()).append("\n\n");
        if (skill.getTriggerDescription() != null)
            md.append("**触发条件**: ").append(skill.getTriggerDescription()).append("\n\n");

        md.append("## 步骤\n\n");
        for (SkillStep step : steps) {
            md.append("### ").append(step.getStepOrder()).append(". ");
            md.append("[").append(step.getStepType()).append("] ");
            md.append(step.getTitle()).append("\n\n");
            if (step.getDescription() != null)
                md.append(step.getDescription()).append("\n\n");
            if (step.getCodeTemplate() != null)
                md.append("```\n").append(step.getCodeTemplate()).append("\n```\n\n");
            if (step.getExpectedOutput() != null)
                md.append("**预期输出**: ").append(step.getExpectedOutput()).append("\n\n");
            if (step.getNotes() != null)
                md.append("> ").append(step.getNotes()).append("\n\n");
        }

        // 保存导出内容
        skill.setExportedContent(md.toString());
        skillMapper.updateById(skill);

        return md.toString();
    }).subscribeOn(Schedulers.boundedElastic());
}
```

### 3.4 T09 — SkillExtractionService System Prompt

```java
private String buildExtractionPrompt(ExtractSkillRequest req) {
    return "你是一个 Skill 提取助手。用户会描述一段工作流程，你需要从中提取结构化的 Skill。\n\n" +
        "请严格输出以下 JSON 格式（不要包含 markdown 代码围栏）：\n" +
        "{\n" +
        "  \"name\": \"Skill 名称\",\n" +
        "  \"description\": \"Skill 描述\",\n" +
        "  \"triggerDescription\": \"触发条件描述\",\n" +
        "  \"steps\": [\n" +
        "    {\n" +
        "      \"title\": \"步骤标题\",\n" +
        "      \"description\": \"步骤详细描述\",\n" +
        "      \"stepType\": \"action|decision|validation|reference\",\n" +
        "      \"codeTemplate\": \"代码模板（如适用）\",\n" +
        "      \"expectedOutput\": \"预期输出（如适用）\",\n" +
        "      \"notes\": \"补充说明（可选）\"\n" +
        "    }\n" +
        "  ]\n" +
        "}\n\n" +
        "要求：\n" +
        "1. steps 至少包含 1 个步骤\n" +
        "2. stepType 必须是 action/decision/validation/reference 之一\n" +
        "3. 为每个步骤生成合理的 codeTemplate 和 expectedOutput\n" +
        "4. 输出纯 JSON，不要包含任何其他文本";
}
```

**JSON 解析容错**：AI 输出可能包含 markdown 代码围栏或前后多余文本。解析时用正则提取 JSON 块：

```java
public Skill parseSkillJson(String rawOutput) {
    // 尝试直接解析
    try {
        return objectMapper.readValue(rawOutput, Skill.class);
    } catch (Exception ignored) {}

    // 用正则提取 ```json ... ``` 或 { ... } 块
    Pattern jsonPattern = Pattern.compile("\\{[\\s\\S]*\"steps\"[\\s\\S]*\\}");
    Matcher matcher = jsonPattern.matcher(rawOutput);
    if (matcher.find()) {
        try {
            return objectMapper.readValue(matcher.group(), Skill.class);
        } catch (Exception e) {
            log.warn("JSON 解析失败: {}", e.getMessage());
        }
    }
    return null;
}
```

### 3.5 T10 — 规则引擎模板匹配

预设模板内置在代码中，不存数据库。匹配逻辑：

```java
// 预设模板库
private static final List<Map<String, Object>> TEMPLATES = List.of(
    Map.of("name", "CRUD API 开发",
           "framework", "spring-boot",
           "keywords", List.of("crud", "api", "rest", "controller"),
           "description", "基于 Spring Boot 的 RESTful CRUD API 开发流程"),
    Map.of("name", "React 组件开发",
           "framework", "react",
           "keywords", List.of("component", "react", "tsx", "hooks"),
           "description", "React 组件开发标准流程，包含类型定义、测试和文档"),
    Map.of("name", "数据库迁移",
           "framework", "postgresql",
           "keywords", List.of("migration", "database", "sql", "schema"),
           "description", "数据库 Schema 变更的标准迁移流程"),
    Map.of("name", "API 测试编写",
           "framework", "",
           "keywords", List.of("test", "testing", "api", "assert"),
           "description", "API 接口测试的标准编写流程")
);
```

**去重逻辑**：生成推荐前查询 `skill_suggestions` 表中该用户已 dismissed 的推荐名称列表，跳过同名模板。

### 3.6 T11 — SkillController SSE 提取

```java
@PostMapping(value = "/extract", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ServerSentEvent<String>> extract(
        @RequestHeader(value = "Authorization", required = false) String authHeader,
        @RequestBody ExtractSkillRequest req) {

    UUID userId = extractUserId(authHeader); // 可能为 null（匿名用户）
    StringBuilder accumulated = new StringBuilder();

    return extractionService.extractSkill(userId, req)
        .filter(chunk -> chunk.getType() != AiChunkType.TOOL_CALL)
        .map(chunk -> {
            if (chunk.getType() == AiChunkType.TEXT) {
                accumulated.append(chunk.getContent());
                return ServerSentEvent.builder(chunk.getContent())
                    .event("text").build();
            } else if (chunk.getType() == AiChunkType.DONE) {
                // 登录用户：异步保存
                if (userId != null) {
                    extractionService.parseAndSave(userId, accumulated.toString(), req)
                        .subscribe();
                }
                return ServerSentEvent.builder("[DONE]")
                    .event("done").build();
            } else {
                return ServerSentEvent.builder("")
                    .event("ping").build();
            }
        });
}
```

**关键**：SSE 流中的 `doOnComplete` 回调不适合保存（因为 `parseAndSave` 需要累积的完整文本）。在 `map` 中捕获 `DONE` 事件时触发保存是更可靠的方式。

### 3.7 T13 — 前端 recordActivityHybrid

```typescript
// LocalActivityStorage.ts 新增
import { api } from '@/api/client'

const STORAGE_MODE_KEY = 'activityStorageMode'

export type StorageMode = 'server' | 'local' | 'hybrid'

export function getStorageMode(): StorageMode {
  const mode = localStorage.getItem(STORAGE_MODE_KEY)
  if (mode === 'server' || mode === 'local' || mode === 'hybrid') return mode
  // 非 Chromium 浏览器默认 server
  if (!('showDirectoryPicker' in window)) return 'server'
  return 'server' // 默认服务端存储
}

export function setStorageMode(mode: StorageMode): void {
  localStorage.setItem(STORAGE_MODE_KEY, mode)
}

export async function recordActivityHybrid(activity: ActivitySummary): Promise<void> {
  const mode = getStorageMode()

  // 本地写入（仅 local/hybrid 模式 + Chromium 浏览器）
  if (mode !== 'server' && 'showDirectoryPicker' in window) {
    try { await recordActivity(activity) } catch { /* 降级忽略 */ }
  }

  // 服务端写入（仅 server/hybrid 模式）
  if (mode !== 'local') {
    try {
      await api.post('/activities', {
        type: activity.type,
        framework: activity.framework,
        keywords: activity.keywords,
        language: activity.language,
        resultCount: activity.resultCount,
      })
    } catch (err) {
      console.warn('服务端行为记录失败:', err)
      // hybrid 模式下失败不阻断，已写本地
    }
  }
}
```

### 3.8 T16 — StorageSettings 浏览器检测

```typescript
// StorageSettings.tsx 关键逻辑
const isChromium = 'showDirectoryPicker' in window

// 非 Chromium 浏览器：自动切换为 server，显示提示
useEffect(() => {
  if (!isChromium && storageMode !== 'server') {
    setStorageMode('server')
    setBrowserWarning('您的浏览器不支持本地文件存储，已自动切换为云端同步模式。推荐使用 Chrome/Edge 以获得完整功能。')
  }
}, [])
```

---

## 4. 测试策略

| 任务 | 测试类型 | 测试文件/位置 | 关键用例 |
|------|---------|--------------|---------|
| T01 | 手动验证 | PostgreSQL 控制台 | 1) 4 张表创建成功 2) 索引存在 3) 外键约束生效 4) 默认值正确 |
| T02 | 编译验证 | — | 1) Model 字段与表列一一对应 2) UUID TypeHandler 正确标注 |
| T03 | 编译验证 | — | 1) JSONB 字段用 JacksonTypeHandler 2) TEXT[] 用 StringArrayTypeHandler |
| T06 | 单元测试 | `backend/src/test/java/.../service/SkillStepServiceTest.java` | 1) replaceSteps 先删后插 2) stepOrder 自动维护 3) 空列表清空所有步骤 |
| T07 | 单元测试 | `backend/src/test/java/.../service/SkillServiceTest.java` | 1) 创建 Skill + Steps 联动 2) 软删除标记 isDeleted 3) 分页查询正确 4) Markdown 导出格式 5) 非 owner 无权访问 |
| T08 | 单元测试 | `backend/src/test/java/.../service/ActivityServiceTest.java` | 1) 正常记录 2) 5 分钟内同 type+keywords 去重 3) cleanup 删除过期数据 |
| T09 | 集成测试 | 手动 SSE 测试 | 1) 匿名用户提取成功 2) 登录用户自动保存 3) AI 输出含 markdown 围栏时 JSON 解析成功 4) 空描述返回错误 |
| T10 | 单元测试 | `backend/src/test/java/.../service/SkillSuggestionServiceTest.java` | 1) 无行为数据时不生成推荐 2) 重复 3+ 次同框架触发模板推荐 3) dismissed 模板不重复推荐 4) accept 转为正式 Skill |
| T11 | 集成测试 | curl / Postman | 1) GET /api/skills 返回分页列表 2) PUT 更新含 steps 3) DELETE 软删除 4) POST /export 返回 Markdown 5) SSE /extract 流式输出 |
| T12 | 集成测试 | curl / Postman | 1) POST /api/activities 记录成功 2) GET 返回分页 3) DELETE /cleanup 清理过期 |
| T13 | 手动验证 | 浏览器控制台 | 1) DemoPage 生成后 Network 面板可见 POST /activities 2) KbPage 搜索后同上 3) 离线时降级到本地写入 |
| T14 | 手动验证 | 浏览器 | 1) 搜索框输入实时过滤 2) 分类 chips 点击过滤 3) 空结果显示提示 |
| T15 | 手动验证 | 浏览器 | 1) 点击 Skill 进入详情 2) 编辑模式修改 name 3) 新增/删除步骤 4) 保存后 version + 1 |
| T16 | 手动验证 | 浏览器 | 1) 切换存储模式后刷新页面保持 2) Firefox 打开自动切换 server 3) 提示信息显示 |

---

## 5. 依赖关系图

```
                        ┌─────────────────────────────────────────┐
                        │            Phase 0: 基础层              │
                        │                                         │
                        │  T01 (V14 迁移)   T05 (DTO)             │
                        │       │                                  │
                        └───────┼──────────────────────────────────┘
                                │
                        ┌───────▼──────────────────────────────────┐
                        │          Phase 1: Model + Mapper         │
                        │                                          │
                        │  T02 (Skill/Step Model)                  │
                        │       │                                  │
                        │  T03 (Suggestion/Activity Model)         │
                        │       │                                  │
                        │       └──────┬───────────────────┐       │
                        │              ▼                   │       │
                        │         T04 (全部 Mapper)        │       │
                        └──────────────┼───────────────────┘       │
                                       │
                        ┌──────────────▼───────────────────────────┐
                        │          Phase 2: 核心 Service            │
                        │                                          │
                        │  ┌──────────┐  ┌──────────┐              │
                        │  │ T06      │  │ T08      │              │
                        │  │ StepSvc  │  │ ActivitySvc             │
                        │  └────┬─────┘  └────┬─────┘              │
                        │       │             │                     │
                        │  ┌────▼─────────────┘                    │
                        │  │ T07 SkillService                      │
                        │  └────┬─────┐                            │
                        │       │     │                             │
                        │  ┌────▼──┐  │                             │
                        │  │ T09   │  │                             │
                        │  │ ExtractSvc                            │
                        │  └───────┘  │                             │
                        │            │                              │
                        │  ┌─────────▼──────────┐                  │
                        │  │ T10 SuggestionSvc   │                  │
                        │  │ (规则引擎)           │                  │
                        │  └────────────────────┘                  │
                        └──────────────┬───────────────────────────┘
                                       │
                        ┌──────────────▼───────────────────────────┐
                        │          Phase 3: Controller              │
                        │                                          │
                        │  ┌──────────────────┐ ┌───────────┐      │
                        │  │ T11 SkillCtrl    │ │ T12 ActCtrl│     │
                        │  └────────┬─────────┘ └─────┬─────┘      │
                        └───────────┼─────────────────┼────────────┘
                                    │                 │
                        ┌───────────▼─────────────────▼────────────┐
                        │          Phase 4: 前端集成 (P0)           │
                        │                                          │
                        │  T13 (行为采集 ← T12)                     │
                        └──────────────────────────────────────────┘

                        ┌──────────────────────────────────────────┐
                        │          Phase 5: 前端增强 (P1)           │
                        │                                          │
                        │  T14 (搜索过滤)  T15 (在线编辑)           │
                        │  T16 (StorageSettings)  T17 (推荐编辑)    │
                        │  T18 (AI 增强推荐)                        │
                        └──────────────────────────────────────────┘

                        ┌──────────────────────────────────────────┐
                        │          Phase 6: 高级功能 (P2)           │
                        │                                          │
                        │  T19 (拖拽排序)  T20 (自定义分类)          │
                        │  T21 (数据迁移)  T22 (同步状态)            │
                        └──────────────────────────────────────────┘
```

---

## 6. 执行顺序与理由

### Phase 0 — 基础层（可并行）

| 顺序 | 任务 | 理由 |
|------|------|------|
| 1 | T01 (V14 迁移) | 所有后续任务依赖表结构，必须最先执行 |
| 1 | T05 (DTO) | 无依赖，可与 T01 并行开发 |

### Phase 1 — Model + Mapper（串行）

| 顺序 | 任务 | 理由 |
|------|------|------|
| 2 | T02 (Skill/Step Model) | 依赖 T01 表结构 |
| 2 | T03 (Suggestion/Activity Model) | 依赖 T01 表结构，可与 T02 并行 |
| 3 | T04 (Mapper) | 依赖 T02 + T03 Model 定义 |

### Phase 2 — 核心 Service（部分并行）

| 顺序 | 任务 | 理由 |
|------|------|------|
| 4 | T06 (StepService) | 依赖 T04，被 T07 依赖 |
| 4 | T08 (ActivityService) | 依赖 T04，与 T06 可并行 |
| 5 | T07 (SkillService) | 依赖 T04 + T06 |
| 6 | T09 (ExtractionService) | 依赖 T07 + ReActAgent |
| 7 | T10 (SuggestionService) | 依赖 T07 + T08，需要行为数据和 Skill 创建能力 |

### Phase 3 — Controller（可并行）

| 顺序 | 任务 | 理由 |
|------|------|------|
| 8 | T11 (SkillController) | 依赖 T07 + T09 + T10 |
| 8 | T12 (ActivityController) | 依赖 T08，与 T11 可并行 |

### Phase 4 — 前端 P0 集成

| 顺序 | 任务 | 理由 |
|------|------|------|
| 9 | T13 (行为采集) | 依赖 T12 ActivityController |

### Phase 5 — 前端 P1 增强（可并行）

| 顺序 | 任务 | 理由 |
|------|------|------|
| 10 | T14 (搜索过滤) | 依赖 T11 |
| 10 | T15 (在线编辑) | 依赖 T11，与 T14 可并行 |
| 10 | T16 (StorageSettings) | 依赖 T12 |
| 10 | T17 (推荐编辑) | 依赖 T11 |
| 11 | T18 (AI 增强推荐) | 依赖 T10 + T09 |

### Phase 6 — 前端 P2 高级功能（可并行）

| 顺序 | 任务 | 理由 |
|------|------|------|
| 12 | T19 (拖拽排序) | 依赖 T15 |
| 12 | T20 (自定义分类) | 依赖 T14 |
| 12 | T21 (数据迁移) | 依赖 T13 + T16 |
| 12 | T22 (同步状态) | 依赖 T16 |

---

## 7. 风险点与缓解

### 7.1 AI 提取 JSON 输出不稳定

- **风险**: LLM 输出可能包含 markdown 代码围栏、前后多余文本、甚至非法 JSON
- **影响**: Skill 提取功能完全不可用
- **缓解**:
  1. System Prompt 中明确要求"纯 JSON，不要包含代码围栏"
  2. 解析时用正则提取 `{...}` 块，容忍前后噪音
  3. 解析失败时返回友好错误提示，引导用户重试
  4. 允许 `maxIterations=2`，给 AI 一次修正机会

### 7.2 行为数据量不足导致推荐质量差

- **风险**: 初期用户行为数据很少，规则引擎无法生成有意义的推荐
- **影响**: "刷新推荐"返回空列表，用户体验差
- **缓解**:
  1. 模板匹配不依赖用户数据量，只要检测到框架/关键词就触发
  2. 首次使用时展示引导文案："持续使用平台后，系统会自动分析你的使用模式"
  3. Phase 2 的 AI 推荐可在数据不足时生成通用推荐

### 7.3 WebFlux 事务一致性

- **风险**: `replaceSteps` 的先删后插操作在非事务上下文中执行可能导致数据丢失
- **影响**: 更新 Skill 步骤时步骤丢失
- **缓解**:
  1. `replaceSteps` 方法加 `@Transactional`
  2. 确保调用链在 `Schedulers.boundedElastic()` 线程中执行（MyBatis Plus 阻塞操作必须在此调度器上）
  3. 替代方案：如果事务有问题，改为"先插后删"策略（先插入新步骤，再删除不在新列表中的旧步骤）

### 7.4 SSE 流中保存时序

- **风险**: SSE 流结束时触发 `parseAndSave`，但累积文本可能不完整（网络中断、客户端断开）
- **影响**: 保存的 Skill 数据不完整
- **缓解**:
  1. 在 `DONE` 事件触发时保存（而非 `doOnComplete`），此时累积文本已完整
  2. 解析失败时不保存，不影响用户体验（前端已展示提取结果）
  3. 保存失败时记录日志，不影响 SSE 流的正常结束

### 7.5 File System Access API 兼容性

- **风险**: Firefox/Safari 不支持 `showDirectoryPicker`，现有 `recordActivity` 函数完全不可用
- **影响**: 非 Chromium 浏览器无法使用本地行为数据存储
- **缓解**:
  1. `recordActivityHybrid` 检测 `showDirectoryPicker` 支持，不支持时跳过本地写入
  2. StorageSettings 自动切换为 `server` 模式并显示友好提示
  3. 服务端存储作为所有浏览器的兜底方案

### 7.6 Flyway 迁移脚本不可修改

- **风险**: V14 脚本提交后发现错误，无法修改（校验和机制）
- **影响**: 需要 V15 修复脚本，增加迁移历史复杂度
- **缓解**:
  1. 开发环境充分测试后再提交
  2. 本地先用 `mvn flyway:migrate` 验证
  3. 确认与现有 V1-V13 迁移无冲突

### 7.7 推荐去重与生命周期

- **风险**: 同一模板被反复推荐（去重逻辑遗漏），或 dismissed 的推荐在新一轮生成中再次出现
- **影响**: 用户体验差，推荐列表重复
- **缓解**:
  1. `generateSuggestions` 开始时查询该用户所有 dismissed 的推荐名称集合
  2. 模板匹配结果过滤掉已 dismissed 的名称
  3. `acceptSuggestion` 时同时检查是否已存在同名 Skill，避免重复创建

---

## 附录：工时估算汇总

| 阶段 | 任务数 | 预估工时 | 优先级 |
|------|--------|---------|--------|
| Phase 0 基础层 | 2 | 2h | P0 |
| Phase 1 Model + Mapper | 2 | 3.5h | P0 |
| Phase 2 核心 Service | 5 | 11h | P0 |
| Phase 3 Controller | 2 | 3.5h | P0 |
| Phase 4 前端 P0 | 1 | 1.5h | P0 |
| Phase 5 前端 P1 | 5 | 11h | P1 |
| Phase 6 前端 P2 | 4 | 7h | P2 |
| **总计** | **22** | **39.5h** | — |

**P0 核心链路工时**: 21.5h（约 3 个工作日）
**P1 体验增强工时**: 11h（约 1.5 个工作日）
**P2 高级功能工时**: 7h（约 1 个工作日）
