# 代码地图 — DevKnowledge

## 架构概述

前后端分离的单体应用。前端 React SPA 通过 REST API + SSE 与后端通信，后端 Spring Boot WebFlux 提供响应式服务，PostgreSQL 存储数据（含 pgvector 向量检索）。

## 目录结构

```
DevKnowledge/
├── frontend/                    # React 前端 (Vite)
│   ├── package.json             # 依赖管理（npm）
│   ├── package-lock.json        # 锁定版本（自动生成，勿手动修改）
│   └── src/
│       ├── api/                 # API 客户端层（按模块拆分）
│       │   ├── client.ts        # HTTP 基础封装 + SSE 流式解析（含 patch 方法）
│       │   ├── auth.ts          # 认证 API
│       │   ├── demos.ts         # Demo 生成 API（SSE）
│       │   ├── kb.ts            # 知识库 API（含 reorderKbs）
│       │   ├── knowledge.ts     # 知识搜索 API
│       │   ├── settings.ts      # AI 配置 API
│       │   ├── embedding.ts     # Embedding 配置 API
│       │   ├── reranker.ts      # Reranker 配置 API
│       │   ├── wiki.ts          # Wiki API（含 authFetch）
│       │   └── skills.ts        # Skills API
│       ├── components/          # UI 组件
│       │   ├── layout/          # Layout + Header + Sidebar
│       │   ├── knowledge/       # 框架卡片 + 搜索栏 + 链接卡片
│       │   └── skills/          # Skill 推荐卡片
│       ├── hooks/
│       │   └── useSSE.ts        # SSE 流式 Hook（使用 demoStore 全局状态）
│       ├── pages/               # 路由页面
│       │   ├── HomePage.tsx     # 首页（四模块入口）
│       │   ├── LoginPage.tsx    # 登录/注册（含记住我）
│       │   ├── KnowledgePage.tsx# 知识搜索 + 框架浏览
│       │   ├── DemoPage.tsx     # Demo 流式生成 + ReAct 可视化 + 历史记录
│       │   ├── SkillsPage.tsx   # Skills 构建
│       │   ├── KbPage.tsx       # 知识库管理（拖拽排序 + 批量删除 + 多文件上传）
│       │   ├── WikiPage.tsx     # Wiki 知识图谱
│       │   ├── SettingsPage.tsx # 设置页（顶部导航切换）
│       │   └── settings/        # 设置子页面
│       │       ├── AiSettings.tsx       # AI 配置管理 + Token 图表
│       │       ├── EmbeddingSettings.tsx # Embedding 配置
│       │       ├── RerankerSettings.tsx  # Reranker 配置
│       │       ├── RagMetrics.tsx        # RAG 检索指标
│       │       └── StorageSettings.tsx   # 数据存储设置
│       ├── stores/              # Zustand 状态管理
│       │   ├── authStore.ts     # 认证状态（token + 用户信息 + 记住我）
│       │   ├── demoStore.ts     # Demo 全局状态（isStreaming/output/events）
│       │   └── notify.tsx       # 全局通知弹窗
│       └── types/
│           ├── api.ts           # TypeScript 类型定义
│           ├── wiki.ts          # Wiki 类型
│           └── skills.ts        # Skills 类型
│
├── backend/                     # Spring Boot 后端 (WebFlux)
│   └── src/main/
│       ├── java/com/devknowledge/
│       │   ├── config/          # 全局配置
│       │   │   ├── SecurityConfig.java        # WebFlux Security 放行规则
│       │   │   ├── CorsConfig.java            # CORS (localhost:5173)
│       │   │   ├── MyBatisPlusConfig.java     # UUID + String[] TypeHandler
│       │   │   └── GlobalExceptionHandler.java# 异常 → JSON
│       │   ├── controller/      # REST 控制器
│       │   │   ├── AuthController.java        # 注册/登录/刷新
│       │   │   ├── SettingsController.java    # AI 配置 CRUD + Token 统计
│       │   │   ├── EmbeddingConfigController.java # Embedding 配置
│       │   │   ├── RerankerConfigController.java  # Reranker 配置
│       │   │   ├── KnowledgeController.java   # 框架/链接/全文搜索
│       │   │   ├── DemoController.java        # Demo SSE 生成 + 历史
│       │   │   ├── KbController.java          # 知识库 CRUD + 文档上传 + 拖拽排序
│       │   │   ├── WikiController.java        # Wiki 页面 CRUD
│       │   │   ├── ActivityController.java    # 用户行为记录
│       │   │   ├── SkillController.java       # Skills CRUD
│       │   │   ├── AdminController.java       # 开发者后台指标/错误/反馈查询
│       │   │   ├── FeedbackController.java    # 用户意见反馈
│       │   │   └── TelemetryController.java   # 前端错误上报
│       │   ├── service/         # 业务逻辑层
│       │   │   ├── AuthService.java           # 注册/登录/JWT
│       │   │   ├── AiConfigService.java       # AI 配置（多配置 + AES 加密）
│       │   │   ├── EmbeddingConfigService.java# Embedding 配置管理
│       │   │   ├── EmbeddingService.java      # /v1/embeddings 调用（WebClient 4MB 缓冲）
│       │   │   ├── EmbeddingUsageService.java # Token 消耗统计
│       │   │   ├── RerankerConfigService.java # Reranker 配置管理
│       │   │   ├── RerankerService.java       # Reranker API 调用
│       │   │   ├── KnowledgeService.java      # 全文搜索
│       │   │   ├── DemoService.java           # Demo 生成（ReAct Agent + RAG 预检索）
│       │   │   ├── KbService.java             # 知识库 + 向量化 + BM25 + RRF
│       │   │   ├── FileParserService.java     # 文件解析（txt/md/pdf/docx）
│       │   │   ├── MarkdownChunker.java       # Markdown AST 切分
│       │   │   ├── JiebaSegmenter.java        # 中文分词（cleanTokens 4 层过滤）
│       │   │   ├── RrfRanker.java             # RRF 融合排序
│       │   │   ├── RequestTiming.java         # 请求与关键阶段计时上下文
│       │   │   ├── RequestObservabilityService.java # 观测、错误、反馈记录与通知
│       │   │   ├── NotificationService.java   # 异步开发者邮件通知
│       │   │   ├── SensitiveDataSanitizer.java # 错误摘要脱敏
│       │   │   ├── AdminService.java          # 开发者后台聚合查询
│       │   │   └── RawFileStorageService.java # 原始文件存储
│       │   │   ├── WikiFileService.java       # Wiki 文件操作
│       │   │   ├── WikiIngestService.java     # Wiki 文档摄取 + LLM 分析
│       │   │   ├── WikiLlmService.java        # Wiki LLM 调用
│       │   │   ├── WikiRetrievalService.java  # Wiki 检索
│       │   │   ├── ActivityService.java       # 用户行为记录
│       │   │   ├── SkillService.java          # Skills CRUD
│       │   │   ├── SkillStepService.java      # Skill 步骤管理
│       │   │   ├── SkillSuggestionService.java# Skill 推荐
│       │   │   └── SkillExtractionService.java# Skill 提取
│       │   ├── service/ai/      # AI 适配层
│       │   │   ├── AiProviderAdapter.java     # 适配器接口（含 ChatMessage + reasoningContent）
│       │   │   ├── AiProviderFactory.java     # 适配器工厂
│       │   │   ├── OpenAiCompatibleAdapter.java # OpenAI 兼容实现（流式文本 + 工具调用）
│       │   │   ├── ReActAgent.java            # ReAct 多轮推理引擎（完成检测 + 死循环检测）
│       │   │   ├── DemoToolProvider.java      # 工具定义（search_kb）
│       │   │   ├── AiFunction.java            # 工具描述
│       │   │   ├── AiChunk.java               # 输出块（含 reasoningContent）
│       │   │   ├── AiChunkType.java           # 块类型枚举
│       │   │   └── ToolHandler.java           # 工具执行接口
│       │   ├── mapper/          # MyBatis Plus Mapper
│       │   │   ├── UserMapper.java
│       │   │   ├── UserAiConfigMapper.java
│       │   │   ├── UserEmbeddingConfigMapper.java
│       │   │   ├── UserRerankerConfigMapper.java
│       │   │   ├── FrameworkMapper.java
│       │   │   ├── KnowledgeLinkMapper.java   # 含 fullTextSearch 自定义 SQL
│       │   │   ├── DemoMapper.java
│       │   │   ├── KnowledgeBaseMapper.java
│       │   │   ├── KbDocumentMapper.java
│       │   │   ├── KbChunkMapper.java         # pgvector 余弦相似度 + BM25 检索 SQL
│       │   │   ├── EmbeddingUsageMapper.java
│       │   │   ├── RagMetricMapper.java
│       │   │   ├── WikiEntityMapper.java
│       │   │   ├── WikiRelationMapper.java
│       │   │   ├── WikiIndexMapper.java
│       │   │   ├── WikiDocumentMapper.java
│       │   │   ├── SkillMapper.java
│       │   │   ├── SkillStepMapper.java
│       │   │   ├── SkillSuggestionMapper.java
│       │   │   ├── RequestTraceMapper.java / RequestSpanMapper.java
│       │   │   ├── ErrorReportMapper.java / UserFeedbackMapper.java
│       │   │   ├── AdminMapper.java          # 后台聚合统计查询
│       │   │   └── UserActivityMapper.java
│       │   ├── model/           # 实体类
│       │   │   ├── User.java / UserAiConfig.java / UserEmbeddingConfig.java
│       │   │   ├── UserRerankerConfig.java
│       │   │   ├── Framework.java / KnowledgeLink.java
│       │   │   ├── KnowledgeBase.java / KbDocument.java / KbChunk.java
│       │   │   ├── Demo.java / EmbeddingUsage.java / RagMetric.java
│       │   │   ├── WikiEntity.java / WikiRelation.java / WikiIndex.java / WikiDocument.java
│       │   │   ├── Skill.java / SkillStep.java / SkillSuggestion.java
│       │   │   ├── RequestTrace.java / RequestSpan.java
│       │   │   ├── ErrorReport.java / UserFeedback.java
│       │   │   ├── UserActivity.java
│       │   │   ├── UuidTypeHandler.java       # UUID ↔ PostgreSQL uuid
│       │   │   ├── StringArrayTypeHandler.java# String[] ↔ text[]
│       │   │   └── VectorTypeHandler.java     # float[] ↔ pgvector
│       │   ├── dto/             # 请求/响应 DTO
│       │   └── security/        # 安全模块
│       │       ├── JwtTokenProvider.java      # JWT 生成/解析
│       │       ├── JwtAuthenticationFilter.java# WebFlux 认证过滤器
│       │       └── AesUtil.java               # AES-256-GCM 加密
│       └── resources/
│           ├── application.yml
│           └── db/migration/    # Flyway 迁移脚本（V1-V20）
│
├── docs/                        # 项目文档（specs/plans/test-data）
├── logs/                        # 应用日志
├── 进度.md                      # 开发进度记录
└── CODE_MAP.md                  # 本文件
```

## 核心模块关系

```
┌─────────────────────────────────────────────────────┐
│                    前端 (React)                       │
│  pages → api/client.ts → useSSE Hook → stores       │
│  demoStore (全局) ← useSSE ← DemoPage               │
└───────────────────────┬─────────────────────────────┘
                        │ HTTP + SSE
┌───────────────────────┴─────────────────────────────┐
│                  后端 (WebFlux)                       │
│                                                      │
│  Controller → Service → Mapper → PostgreSQL          │
│       │          │                                    │
│       │     ┌────┴────┐                              │
│       │     │ AI 层    │                              │
│       │     │ Adapter  │ ←→ 外部 AI API               │
│       │     │ ReAct    │                              │
│       │     │ Tools    │                              │
│       │     └─────────┘                              │
│       │                                              │
│  Security: JWT Filter → TokenProvider → AesUtil      │
└─────────────────────────────────────────────────────┘
```

## 关键文件

| 文件 | 重要性 | 说明 |
|------|--------|------|
| `frontend/src/api/client.ts` | ★★★ | HTTP 封装 + SSE 流式解析，所有 API 的基础 |
| `frontend/src/hooks/useSSE.ts` | ★★★ | SSE Hook，使用 demoStore 全局状态 |
| `frontend/src/stores/demoStore.ts` | ★★★ | Demo 全局状态，切换页面内容不丢失 |
| `frontend/src/stores/authStore.ts` | ★★☆ | 认证状态，token 管理 + 记住我 |
| `frontend/src/components/tour/GuidedTour.tsx` | ★★☆ | 首次使用引导编排器（driver.js 聚光灯 + 跨页导航 + 降级兜底） |
| `backend/.../service/AdminHousekeepingService.java` | ★★☆ | 观测数据保留策略（traces 14 天定时清理） |
| `backend/.../security/JwtTokenProvider.java` | ★★★ | JWT 生成/解析，所有认证的基础 |
| `backend/.../security/AesUtil.java` | ★★★ | API Key 加密/脱敏 |
| `backend/.../service/ai/ReActAgent.java` | ★★★ | ReAct 多轮推理引擎 |
| `backend/.../service/ai/OpenAiCompatibleAdapter.java` | ★★★ | AI API 流式调用（文本实时 + 工具调用统一） |
| `backend/.../service/ai/AiChunk.java` | ★★☆ | AI 输出块（含 reasoningContent） |
| `backend/.../service/DemoService.java` | ★★★ | Demo 生成服务（RAG 预检索 + 保存逻辑） |
| `backend/.../service/KbService.java` | ★★☆ | 知识库 + 向量化 + BM25 + RRF |
| `backend/.../service/EmbeddingService.java` | ★★☆ | Embedding 调用（WebClient 4MB 缓冲） |
| `backend/.../service/JiebaSegmenter.java` | ★★☆ | 中文分词（cleanTokens 4 层过滤） |
| `backend/.../mapper/KbChunkMapper.java` | ★★☆ | pgvector 余弦相似度 + BM25 检索 SQL |
| `backend/resources/application.yml` | ★★☆ | 数据库/JWT/AI 配置 |
| `backend/resources/db/migration/` | ★★☆ | 数据库迁移（不可修改已提交的文件） |

## 数据流

### Demo 生成（SSE 流式）
```
前端 DemoPage → POST /api/demos/generate (SSE)
  → DemoController → DemoService
    → RAG 预检索（Mono.fromCallable + boundedElastic）
    → ReActAgent（多轮推理，最多 5 轮）
      → OpenAiCompatibleAdapter（文本实时发送 + 工具调用统一发送）
      → DemoToolProvider（search_kb 工具）
    → 每个 AiChunk 实时推送到前端（打字机效果）
  → doOnComplete / doOnCancel 保存 Demo 记录
```

### 知识库 RAG 检索（三阶段）
```
文档上传 → FileParserService 解析 → MarkdownChunker 段落切分
  → JiebaSegmenter 分词（cleanTokens 4 层过滤）→ 存入 tsv
  → EmbeddingService 批量向量化（batch=5）→ 存入 kb_chunks (pgvector)

Demo 生成时（三阶段检索）：
  → BM25 关键词检索（JiebaSegmenter.buildOrTsQuery → to_tsquery + ts_rank_cd）
    查询侧与入库侧共用同一套 Jieba 分词，OR 语义保召回
  → EmbeddingService 查询向量化 → pgvector 余弦相似度
  → RRF 融合排序（k=60）
  → Reranker 精排（可选，失败优雅降级）
```

### AI 配置
```
用户配置 → AES-256-GCM 加密存储
  → AiConfigService / EmbeddingConfigService / RerankerConfigService
  → activateConfig: deactivateAll → 更新 is_active
  → 前端：配置列表 + 删除按钮（列表项右侧）+ 设为默认
```

## 常见修改场景

| 场景 | 涉及文件 |
|------|---------|
| 新增 API 端点 | controller/ + service/ + 前端 api/ + pages/ |
| 新增数据库表 | model/ + mapper/ + `db/migration/V20__xxx.sql` |
| 修改 AI 调用逻辑 | `service/ai/` 目录（Adapter/ReAct/AiChunk） |
| 新增 ReAct 工具 | `DemoToolProvider.java` 注册 + 对应 Service |
| 修改页面 UI | `pages/` + `components/` |
| 修改认证逻辑 | `security/` + `SecurityConfig.java` |
| 修改 RAG 检索 | `KbService.java` + `KbChunkMapper.java` + `JiebaSegmenter.java` |
| 修改 Embedding | `EmbeddingService.java` + `EmbeddingConfigService.tsx` |
| 修改分词逻辑 | `JiebaSegmenter.java`（cleanTokens 管道） |
