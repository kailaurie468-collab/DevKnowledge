# Skills 模块架构设计

> **版本**: 1.0
> **日期**: 2026-06-09
> **状态**: Draft
> **基于 PRD**: `docs/superpowers/specs/skills-module-prd.md`

---

## 1. 系统架构概览

### 1.1 组件关系图

```
┌─────────────────────────────────────────────────────────────────┐
│                        Frontend (React)                         │
│                                                                 │
│  SkillsPage ──── useSSE ──── api/skills.ts ──── api/client.ts  │
│       │                                     │                   │
│  SkillSuggestionCard                  StorageSettings           │
│       │                                     │                   │
│  LocalActivityStorage ◄─── recordActivity() 调用点              │
│       │                                                          │
│  DemoPage / KbPage / KnowledgePage ── recordActivity()          │
└────────────────────────────┬────────────────────────────────────┘
                             │ HTTP / SSE
┌────────────────────────────▼────────────────────────────────────┐
│                     Backend (Spring Boot WebFlux)                │
│                                                                 │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────────┐        │
│  │SkillController│  │SuggestionCtrl│  │ActivityController│       │
│  └──────┬───────┘  └──────┬───────┘  └───────┬────────┘        │
│         │                 │                   │                  │
│  ┌──────▼───────┐  ┌──────▼───────┐  ┌───────▼────────┐        │
│  │ SkillService  │  │SuggestionSvc │  │ ActivityService │        │
│  └──────┬───────┘  └──────┬───────┘  └───────┬────────┘        │
│         │                 │                   │                  │
│  ┌──────▼───────┐  ┌──────▼───────┐  ┌───────▼────────┐        │
│  │SkillMapper    │  │SuggestionMapper│ │ActivityMapper  │        │
│  │SkillStepMapper│  └──────────────┘  └────────────────┘        │
│  └──────────────┘                                                │
│                                                                 │
│  ┌─────────────────────────────────────┐                        │
│  │     SkillExtractionService          │                        │
│  │  (ReActAgent + custom prompt)       │                        │
│  └──────────────┬──────────────────────┘                        │
│                 │                                                │
│  ┌──────────────▼──────────────────────┐                        │
│  │  ReActAgent (existing)              │                        │
│  │  AiProviderFactory (existing)       │                        │
│  └─────────────────────────────────────┘                        │
│                                                                 │
│  ┌─────────────────────────────────────┐                        │
│  │  PostgreSQL                         │                        │
│  │  skills / skill_steps /             │                        │
│  │  skill_suggestions / user_activities│                        │
│  └─────────────────────────────────────┘                        │
└─────────────────────────────────────────────────────────────────┘
```

### 1.2 新增/修改文件总览

**后端新增 (14 files):**
- Controller: `SkillController.java`, `ActivityController.java`
- Service: `SkillService.java`, `SkillStepService.java`, `SkillSuggestionService.java`, `ActivityService.java`, `SkillExtractionService.java`
- Model: `Skill.java`, `SkillStep.java`, `SkillSuggestion.java`, `UserActivity.java`
- Mapper: `SkillMapper.java`, `SkillStepMapper.java`, `SkillSuggestionMapper.java`, `UserActivityMapper.java`
- DTO: `ExtractSkillRequest.java`, `SkillExportResponse.java`, `ActivityRequest.java`
- Migration: `V14__create_skills_tables.sql`

**后端修改 (0 files):** 无需修改现有文件

**前端新增 (0 files):** 无需新增文件

**前端修改 (5 files):**
- `pages/SkillsPage.tsx` — 补全搜索/过滤/编辑功能
- `pages/settings/StorageSettings.tsx` — 云端同步实际生效
- `pages/DemoPage.tsx` — 接入 recordActivity
- `pages/KbPage.tsx` — 接入 recordActivity
- `storage/LocalActivityStorage.ts` — 增加服务端同步能力

---

## 2. 后端架构

### 2.1 数据库迁移 — V14

```sql
-- V14__create_skills_tables.sql

-- Skills 主表
CREATE TABLE skills (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name                VARCHAR(200) NOT NULL,
    description         TEXT,
    category            VARCHAR(50),
    framework_id        UUID REFERENCES frameworks(id),
    trigger_description TEXT,
    exported_content    TEXT,
    version             INTEGER NOT NULL DEFAULT 1,
    is_public           BOOLEAN NOT NULL DEFAULT false,
    is_deleted          BOOLEAN NOT NULL DEFAULT false,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_skills_user_id ON skills(user_id);
CREATE INDEX idx_skills_user_category ON skills(user_id, category);
CREATE INDEX idx_skills_user_updated ON skills(user_id, updated_at DESC);

-- Skill 步骤表
CREATE TABLE skill_steps (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    skill_id        UUID NOT NULL REFERENCES skills(id) ON DELETE CASCADE,
    step_order      INTEGER NOT NULL,
    title           VARCHAR(300) NOT NULL,
    description     TEXT,
    step_type       VARCHAR(20) NOT NULL DEFAULT 'action',
    code_template   TEXT,
    expected_output TEXT,
    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_skill_steps_skill_id ON skill_steps(skill_id, step_order);

-- Skill 推荐表
CREATE TABLE skill_suggestions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name                VARCHAR(200) NOT NULL,
    description         TEXT,
    trigger_description TEXT,
    category            VARCHAR(50),
    suggested_steps     JSONB NOT NULL DEFAULT '[]',
    source_summary      TEXT,
    status              VARCHAR(20) NOT NULL DEFAULT 'pending',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_skill_suggestions_user ON skill_suggestions(user_id, status);

-- 用户行为记录表
CREATE TABLE user_activities (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type        VARCHAR(30) NOT NULL,
    framework   VARCHAR(50),
    keywords    TEXT[] DEFAULT '{}',
    language    VARCHAR(50),
    result_count INTEGER,
    metadata    JSONB,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_user_activities_user_time ON user_activities(user_id, created_at DESC);
CREATE INDEX idx_user_activities_type ON user_activities(user_id, type);
```

### 2.2 Model 层

所有 Model 遵循现有模式：`@Data` + `@TableName` + `@TableId(type = IdType.INPUT)` + `UuidTypeHandler`。

**Skill.java**
```java
@Data
@TableName("skills")
public class Skill {
    @TableId(type = IdType.INPUT)
    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID id;
    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID userId;
    private String name;
    private String description;
    private String category;
    @TableField(typeHandler = UuidTypeHandler.class)
    private UUID frameworkId;
    private String triggerDescription;
    private String exportedContent;
    private Integer version;
    private Boolean isPublic;
    private Boolean isDeleted;
    private Instant createdAt;
    private Instant updatedAt;
}
```

**SkillStep.java** — 同模式，`skillId` 外键 + `stepOrder/stepType/codeTemplate/expectedOutput/notes` 字段。

**SkillSuggestion.java** — 同模式，`suggestedSteps` 用 `JSONB` 存储（MyBatis Plus `JacksonTypeHandler`），`status` 为 `pending/accepted/dismissed`。

**UserActivity.java** — 同模式，`keywords` 用 `ArrayTypeHandler`（PostgreSQL `TEXT[]`），`metadata` 用 `JacksonTypeHandler`（JSONB）。

### 2.3 Mapper 层

所有 Mapper 继承 `BaseMapper<T>`，复杂查询用 `@Select` 注解。

```java
@Mapper
public interface SkillMapper extends BaseMapper<Skill> {
    // MyBatis Plus LambdaQueryWrapper 处理大部分查询，无需自定义 SQL
}

@Mapper
public interface SkillStepMapper extends BaseMapper<SkillStep> {
    // 按 skillId 批量查询步骤，ORDER BY step_order
}

@Mapper
public interface SkillSuggestionMapper extends BaseMapper<SkillSuggestion> {
    // 按 userId + status 查询推荐
}

@Mapper
public interface UserActivityMapper extends BaseMapper<UserActivity> {
    @Select("SELECT framework, COUNT(*) as cnt FROM user_activities " +
            "WHERE user_id = #{userId} AND created_at > #{since} " +
            "GROUP BY framework ORDER BY cnt DESC LIMIT #{limit}")
    List<Map<String, Object>> getTopFrameworks(UUID userId, Instant since, int limit);

    @Select("SELECT unnest(keywords) as kw, COUNT(*) as cnt FROM user_activities " +
            "WHERE user_id = #{userId} AND created_at > #{since} " +
            "GROUP BY kw ORDER BY cnt DESC LIMIT #{limit}")
    List<Map<String, Object>> getTopKeywords(UUID userId, Instant since, int limit);
}
```

### 2.4 Service 层

#### 2.4.1 SkillService

核心职责：Skill CRUD + 导出 Markdown。

```
方法签名（均为 Mono/Flux 返回类型）:
- createSkill(UUID userId, Skill skill, List<SkillStep> steps) → Mono<Skill>
- getUserSkills(UUID userId, String category, String keyword, int page, int size) → Mono<List<Skill>>
- getSkillById(UUID id, UUID userId) → Mono<Skill>（含 steps）
- updateSkill(UUID id, UUID userId, SkillUpdateRequest req) → Mono<Skill>
- deleteSkill(UUID id, UUID userId) → Mono<Void>（软删除）
- exportToMarkdown(UUID id, UUID userId) → Mono<String>
```

所有阻塞 ORM 操作用 `Schedulers.boundedElastic()` 包装，遵循 `KbService` 模式。

#### 2.4.2 SkillStepService

```
- getStepsBySkillId(UUID skillId) → List<SkillStep>
- replaceSteps(UUID skillId, List<SkillStep> steps) → void（先删后插，事务）
```

#### 2.4.3 SkillSuggestionService

```
- getSuggestions(UUID userId) → Mono<List<SkillSuggestion>>
- updateSuggestion(UUID id, UUID userId, Partial update) → Mono<SkillSuggestion>
- acceptSuggestion(UUID id, UUID userId) → Mono<Skill>（转为正式 Skill + 删除 Suggestion）
- dismissSuggestion(UUID id, UUID userId) → Mono<Void>
```

#### 2.4.4 ActivityService

```
- recordActivity(UUID userId, ActivityRequest req) → Mono<Void>（异步写入，不阻塞主流程）
- getUserActivities(UUID userId, int page, int size) → Mono<List<UserActivity>>
- cleanup(UUID userId, int keepDays) → Mono<Integer>
```

`recordActivity` 内置 5 分钟去重逻辑：查询 `user_activities` 表中同一 `userId + type + keywords` 在 5 分钟内的记录，存在则跳过。

#### 2.4.5 SkillExtractionService

核心职责：调用 ReActAgent 从用户描述中提取结构化 Skill。

**设计决策：使用 ReActAgent 而非直接 API 调用**

理由：
1. **统一 AI 调用路径** — 所有 AI 功能通过 ReActAgent 执行，复用 `AiProviderFactory` + `UserAiConfig` 配置体系
2. **流式输出** — ReActAgent 返回 `Flux<AiChunk>`，天然支持 SSE
3. **扩展性** — 后续可为 Skill 提取添加工具（如搜索已有 Skill 避免重复）
4. **错误处理** — ReActAgent 内置死循环检测、最大轮数限制、完成信号检测

**实现方案：**

```java
@Service
@RequiredArgsConstructor
public class SkillExtractionService {

    private final ReActAgent reactAgent;
    private final AiConfigService aiConfigService;
    private final SkillService skillService;
    private final ObjectMapper objectMapper;

    /**
     * 流式提取 Skill
     * maxIterations=1（无需工具调用，单轮生成即可）
     */
    public Flux<AiChunk> extractSkill(UUID userId, ExtractSkillRequest req) {
        UserAiConfig config = aiConfigService.getActiveConfigEntity(userId);
        String systemPrompt = buildExtractionPrompt(req);
        String userMessage = req.getDescription();

        return reactAgent.run(systemPrompt, userMessage,
                List.of(), Map.of(), config, 1);
    }

    /**
     * 提取完成后解析 JSON 并保存
     * Controller 在 SSE 流结束后调用
     */
    public Mono<Skill> parseAndSave(UUID userId, String rawOutput,
                                      ExtractSkillRequest req) {
        // 从 AI 输出中提取 JSON 块
        Skill parsed = parseSkillJson(rawOutput);
        if (parsed == null) return Mono.empty();
        parsed.setCategory(req.getCategory());
        parsed.setFrameworkId(
            req.getFrameworkId() != null ? UUID.fromString(req.getFrameworkId()) : null);
        return skillService.createSkill(userId, parsed, parsed.getSteps());
    }
}
```

**System Prompt 设计要点：**
- 指示 AI 输出合法 JSON，schema 与前端 `Skill` 类型严格一致
- `steps[].stepType` 限定为 `action | decision | validation | reference`
- 为每个 step 生成 `codeTemplate` 和 `expectedOutput`
- 输出格式：纯 JSON，不包含 markdown 代码围栏

### 2.5 推荐引擎 — SkillSuggestionService (Phase 1 规则引擎)

**设计决策：Phase 1 使用规则引擎，Phase 2 再接入 LLM**

理由：
1. 规则引擎零 AI 成本，响应快（< 200ms）
2. 可独立验证数据采集是否正确
3. LLM 推荐依赖行为数据积累，Phase 1 先跑通数据流

**规则引擎逻辑：**

```java
public Mono<List<SkillSuggestion>> generateSuggestions(UUID userId) {
    Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);

    // 1. 获取用户近 30 天行为数据
    List<UserActivity> activities = activityMapper.selectList(
        new LambdaQueryWrapper<UserActivity>()
            .eq(UserActivity::getUserId, userId)
            .ge(UserActivity::getCreatedAt, thirtyDaysAgo));

    // 2. 频率分析：统计 top framework + keyword 组合
    Map<String, Integer> frameworkFreq = new HashMap<>();
    Map<String, Integer> keywordFreq = new HashMap<>();
    for (UserActivity a : activities) {
        if (a.getFramework() != null)
            frameworkFreq.merge(a.getFramework(), 1, Integer::sum);
        if (a.getKeywords() != null)
            for (String kw : a.getKeywords())
                keywordFreq.merge(kw, 1, Integer::sum);
    }

    // 3. 模式识别：同一操作类型重复 3+ 次 → 触发推荐
    Map<String, Long> typeCount = activities.stream()
        .collect(Collectors.groupingBy(UserActivity::getType, Collectors.counting()));

    // 4. 模板匹配：从预设模板库中匹配
    List<SkillSuggestion> suggestions = matchTemplates(frameworkFreq, keywordFreq, typeCount);

    // 5. 去重：排除已 dismissed 的推荐
    // 6. 保存并返回
}
```

**预设模板库（内置代码中）：**
- CRUD API 开发（后端框架 detected 时触发）
- React 组件开发（React detected 时触发）
- 数据库迁移（database keyword detected 时触发）
- API 测试编写（testing keyword detected 时触发）

### 2.6 Controller 层

#### SkillController.java

```java
@RestController
@RequestMapping("/api/skills")
@RequiredArgsConstructor
public class SkillController {

    private final SkillService skillService;
    private final SkillExtractionService extractionService;
    private final SkillSuggestionService suggestionService;
    private final JwtTokenProvider jwtTokenProvider;

    // SSE 流式提取（认证可选）
    @PostMapping(value = "/extract", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> extract(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody ExtractSkillRequest req);

    // CRUD 端点（认证必须）
    @GetMapping
    public Mono<ResponseEntity<List<Skill>>> getSkills(...);
    @GetMapping("/{id}")
    public Mono<ResponseEntity<Skill>> getSkill(...);
    @PutMapping("/{id}")
    public Mono<ResponseEntity<Skill>> updateSkill(...);
    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deleteSkill(...);

    // 导出
    @PostMapping("/{id}/export")
    public Mono<ResponseEntity<SkillExportResponse>> exportSkill(...);
    @GetMapping("/{id}/export/download")
    public Mono<ResponseEntity<Resource>> downloadSkill(...);

    // 推荐
    @GetMapping("/suggestions")
    public Mono<ResponseEntity<List<SkillSuggestion>>> getSuggestions(...);
    @PostMapping("/suggestions/refresh")
    public Mono<ResponseEntity<Void>> refreshSuggestions(...);
    @PutMapping("/suggestions/{id}")
    public Mono<ResponseEntity<SkillSuggestion>> updateSuggestion(...);
    @PostMapping("/suggestions/{id}/accept")
    public Mono<ResponseEntity<Skill>> acceptSuggestion(...);
    @PostMapping("/suggestions/{id}/dismiss")
    public Mono<ResponseEntity<Void>> dismissSuggestion(...);
}
```

#### ActivityController.java

```java
@RestController
@RequestMapping("/api/activities")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping
    public Mono<ResponseEntity<Void>> recordActivity(...);
    @GetMapping
    public Mono<ResponseEntity<List<UserActivity>>> getActivities(...);
    @DeleteMapping("/cleanup")
    public Mono<ResponseEntity<Integer>> cleanup(...);
}
```

---

## 3. 前端架构

### 3.1 修改文件清单

| 文件 | 修改内容 |
|------|---------|
| `pages/SkillsPage.tsx` | 增加搜索框、分类过滤 chips、Skill 在线编辑模式 |
| `pages/settings/StorageSettings.tsx` | 云端同步选项实际写入 localStorage + 调用 Activity API；非 Chromium 浏览器检测与提示 |
| `pages/DemoPage.tsx` | 生成完成时调用 `recordActivity({ type: 'demo_generate', ... })` |
| `pages/KbPage.tsx` | 搜索时调用 `recordActivity({ type: 'kb_search', ... })` |
| `storage/LocalActivityStorage.ts` | 增加 `recordToServer()` 方法，根据存储模式决定写入目标 |

### 3.2 Activity 采集集成点

**采集策略：双写（本地 + 服务端），由存储模式控制**

```typescript
// storage/LocalActivityStorage.ts 新增
export async function recordActivityHybrid(activity: ActivitySummary): Promise<void> {
  const mode = getStorageMode() // 从 localStorage 读取用户偏好

  // 本地写入（Chromium 浏览器 + 已授权目录）
  if (mode !== 'cloud' && dirHandle) {
    await recordActivity(activity) // 现有逻辑
  }

  // 服务端写入（登录用户）
  if (mode !== 'local') {
    try {
      await api.post('/activities', activity)
    } catch (err) {
      console.warn('服务端记录失败，降级到本地:', err)
      if (dirHandle) await recordActivity(activity)
    }
  }
}
```

**各页面调用示例：**

```typescript
// DemoPage.tsx — 生成完成时
onDone: () => {
  recordActivityHybrid({
    type: 'demo_generate',
    framework: selectedFramework,
    keywords: extractKeywords(prompt),
    language: selectedLanguage,
    ts: Date.now()
  })
}

// KbPage.tsx — 搜索时
const handleSearch = () => {
  recordActivityHybrid({
    type: 'kb_search',
    keywords: [query],
    resultCount: results.length,
    ts: Date.now()
  })
}
```

### 3.3 SkillsPage 增强

**搜索与过滤：**
- 列表顶部增加搜索输入框，`onChange` 时调用 `skillsApi.getSkills({ keyword })` （后端模糊匹配）
- 分类 chips：`['frontend', 'backend', 'devops', 'database', 'testing', 'other']`
- 框架下拉：从 `frameworksApi` 获取列表

**Skill 在线编辑：**
- 复用 `SkillSuggestionCard` 的编辑模式 UI
- 点击 Skill 列表项进入详情，详情面板增加"编辑"按钮
- 编辑模式下可修改 name/description/category/steps
- 保存时调用 `skillsApi.updateSkill(id, data)`

### 3.4 StorageSettings 增强

```
现有问题：
- storageMode 状态仅为 React state，刷新后丢失
- 云端同步选项无实际效果

修改方案：
1. storageMode 持久化到 localStorage('activityStorageMode')
2. 非 Chromium 浏览器检测：!('showDirectoryPicker' in window)
   → 自动切换为 'cloud'，显示提示："您的浏览器不支持本地存储，已自动切换为云端同步"
3. 云端同步增加状态显示：最后同步时间、数据条数
4. 增加"上传本地数据到云端"按钮（P1）
```

---

## 4. 数据流

### 4.1 Skill 提取流 (SSE)

```
用户输入描述
    │
    ▼
前端 SkillsPage.handleExtract()
    │
    ▼ POST /api/skills/extract (SSE)
    │
    ▼ SkillController.extract()
    │  ├─ extractUserId (可选，匿名也允许)
    │  └─ SkillExtractionService.extractSkill()
    │
    ▼ ReActAgent.run(systemPrompt, userMessage, tools=[], handlers={}, config, maxIterations=1)
    │
    ▼ AiProviderAdapter.streamWithTools() → Flux<AiChunk>
    │  ├─ AiChunkType.TEXT → SSE event: text
    │  └─ AiChunkType.DONE → SSE event: done
    │
    ▼ SSE 流结束
    │
    ▼ (登录用户) SkillExtractionService.parseAndSave()
    │  ├─ 从累积文本中提取 JSON
    │  ├─ 解析为 Skill + SkillStep[]
    │  └─ skillService.createSkill() → 写入 DB
    │
    ▼ 前端 onDone → 刷新 Skills 列表
```

### 4.2 推荐生成流

```
用户点击"刷新推荐"
    │
    ▼ POST /api/skills/suggestions/refresh
    │
    ▼ SkillSuggestionService.generateSuggestions(userId)
    │  ├─ ActivityService.getActivities(userId, 30天)
    │  ├─ 规则引擎分析
    │  │   ├─ 频率分析 (framework + keyword)
    │  │   ├─ 模式识别 (重复 3+ 次)
    │  │   └─ 模板匹配
    │  ├─ 去重 (排除已有 dismissed)
    │  └─ 保存到 skill_suggestions 表
    │
    ▼ GET /api/skills/suggestions → 返回 pending 列表
    │
    ▼ 前端渲染 SkillSuggestionCard
    │
    ▼ 用户操作:
    │  ├─ 采纳 → POST /{id}/accept → 转为 Skill + 删除 Suggestion
    │  ├─ 编辑 → PUT /{id} → 更新 Suggestion
    │  └─ 忽略 → POST /{id}/dismiss → status = dismissed
```

### 4.3 行为记录流

```
用户操作 (搜索/生成/点击)
    │
    ▼ 前端 recordActivityHybrid(activity)
    │
    ├─ [本地路径] recordActivity() → JSONL 文件写入 (Chromium)
    │
    └─ [云端路径] POST /api/activities
       │
       ▼ ActivityController.recordActivity()
       │
       ▼ ActivityService.recordActivity()
       │  ├─ 5 分钟去重检查
       │  └─ user_activities 表 INSERT
       │
       ▼ (异步) 不阻塞主流程，返回 200
```

---

## 5. 关键设计决策

### 5.1 Skill 提取：ReAct Agent vs 直接 API 调用

| 维度 | ReAct Agent | 直接 API 调用 |
|------|------------|--------------|
| AI 调用统一性 | 统一路径，复用配置 | 需单独管理 config |
| 流式输出 | 天然支持 Flux<AiChunk> | 需自行实现流式解析 |
| 工具扩展 | 可添加搜索工具 | 无工具能力 |
| 复杂度 | 较高（递归调用） | 较低 |
| 成本 | 相同（maxIterations=1 时等价） | 相同 |

**决策：使用 ReActAgent，maxIterations=1**

理由：统一架构，未来可扩展（如提取时搜索已有 Skill 避免重复）。单轮迭代时与直接调用无性能差异。

### 5.2 推荐引擎：规则 vs LLM

**决策：Phase 1 规则引擎，Phase 2 LLM 增强**

理由：
1. 规则引擎零 AI 成本，响应 < 200ms
2. 行为数据需要积累期，LLM 推荐在数据不足时效果差
3. 规则引擎可独立验证数据采集质量
4. Phase 2 的 LLM 推荐可作为规则推荐的补充，两者可共存

### 5.3 Activity 存储：服务端优先 vs 混合

**决策：混合模式（服务端为主，本地为辅）**

| 模式 | 说明 | 默认 |
|------|------|------|
| `server` | 仅写服务端 | 是（登录用户） |
| `local` | 仅写本地 | Chromium 离线用户 |
| `hybrid` | 双写 | 网络不稳定环境 |

理由：
1. 服务端存储跨设备同步，是推荐引擎的数据源
2. 本地存储作为离线降级，Chromium 浏览器可用时保留
3. 非 Chromium 浏览器自动降级为 `server` 模式

### 5.4 recordActivity() 接入方式

**决策：在前端关键路径直接调用，不使用 AOP/拦截器**

理由：
1. 行为记录是前端业务逻辑（需要提取 keywords/framework 等语义信息），不适合后端 AOP
2. 前端已有 `recordActivity()` 函数定义，只需在各页面接入
3. 异步调用 + try-catch 包裹，不影响主流程性能
4. 5 分钟去重在后端 Service 层实现

---

## 6. 文件清单

### 后端新增文件

| 文件路径 | 说明 |
|---------|------|
| `backend/src/main/resources/db/migration/V14__create_skills_tables.sql` | 4 张表 + 索引 |
| `backend/src/main/java/com/devknowledge/model/Skill.java` | Skills 实体 |
| `backend/src/main/java/com/devknowledge/model/SkillStep.java` | Steps 实体 |
| `backend/src/main/java/com/devknowledge/model/SkillSuggestion.java` | Suggestions 实体 |
| `backend/src/main/java/com/devknowledge/model/UserActivity.java` | Activities 实体 |
| `backend/src/main/java/com/devknowledge/mapper/SkillMapper.java` | Skills Mapper |
| `backend/src/main/java/com/devknowledge/mapper/SkillStepMapper.java` | Steps Mapper |
| `backend/src/main/java/com/devknowledge/mapper/SkillSuggestionMapper.java` | Suggestions Mapper |
| `backend/src/main/java/com/devknowledge/mapper/UserActivityMapper.java` | Activities Mapper |
| `backend/src/main/java/com/devknowledge/dto/ExtractSkillRequest.java` | 提取请求 DTO |
| `backend/src/main/java/com/devknowledge/dto/SkillExportResponse.java` | 导出响应 DTO |
| `backend/src/main/java/com/devknowledge/dto/ActivityRequest.java` | 行为记录请求 DTO |
| `backend/src/main/java/com/devknowledge/service/SkillService.java` | Skill CRUD |
| `backend/src/main/java/com/devknowledge/service/SkillStepService.java` | Steps 管理 |
| `backend/src/main/java/com/devknowledge/service/SkillSuggestionService.java` | 推荐引擎 + CRUD |
| `backend/src/main/java/com/devknowledge/service/ActivityService.java` | 行为数据采集 |
| `backend/src/main/java/com/devknowledge/service/SkillExtractionService.java` | AI 提取 |
| `backend/src/main/java/com/devknowledge/controller/SkillController.java` | Skills + Suggestions API |
| `backend/src/main/java/com/devknowledge/controller/ActivityController.java` | Activities API |

### 前端修改文件

| 文件路径 | 修改内容 |
|---------|---------|
| `frontend/src/pages/SkillsPage.tsx` | 搜索框、分类过滤、Skill 编辑模式 |
| `frontend/src/pages/settings/StorageSettings.tsx` | 云端同步生效、浏览器检测、同步状态 |
| `frontend/src/pages/DemoPage.tsx` | 接入 recordActivityHybrid (demo_generate) |
| `frontend/src/pages/KbPage.tsx` | 接入 recordActivityHybrid (kb_search) |
| `frontend/src/storage/LocalActivityStorage.ts` | 增加 recordActivityHybrid + 服务端同步 |

---

## 7. 依赖关系与实施顺序

```
Phase 0: 数据库
  └─ V14__create_skills_tables.sql (无依赖)

Phase 1: Model + Mapper (依赖 Phase 0)
  └─ 4 个 Model + 4 个 Mapper

Phase 2: 核心 Service (依赖 Phase 1)
  ├─ ActivityService (独立)
  ├─ SkillService + SkillStepService (相互依赖)
  └─ SkillExtractionService (依赖 SkillService + ReActAgent)

Phase 3: 推荐引擎 (依赖 Phase 2)
  └─ SkillSuggestionService (依赖 ActivityService + SkillService)

Phase 4: Controller (依赖 Phase 2 + 3)
  ├─ SkillController (依赖 SkillService + SkillExtractionService + SkillSuggestionService)
  └─ ActivityController (依赖 ActivityService)

Phase 5: 前端集成 (依赖 Phase 4)
  ├─ StorageSettings.tsx 修改 (依赖 ActivityController)
  ├─ DemoPage.tsx / KbPage.tsx 接入 (依赖 ActivityController)
  └─ SkillsPage.tsx 增强 (依赖 SkillController 全部端点)
```

**推荐并行项：**
- Phase 0 和前端 DTO 类型定义可并行
- Phase 2 中 ActivityService 和 SkillService 可并行开发
- Phase 5 中各页面修改可并行

---

## 8. 风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| AI 提取输出 JSON 格式不稳定 | 提取失败 | Prompt 中严格约束输出格式 + 解析时容错（正则提取 JSON 块） |
| 行为数据量不足导致推荐质量差 | 初期推荐无意义 | Phase 1 使用模板匹配兜底，不完全依赖用户数据 |
| ReActAgent 单轮迭代无法完成复杂提取 | 提取结果不完整 | 允许 maxIterations=2，给 AI 一次修正机会 |
| 5 分钟去重窗口可能漏记 | 数据不准确 | 去重基于 type + keywords 组合，精确匹配 |
