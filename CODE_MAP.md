# 代码地图 — DevKnowledge

## 架构概述

前后端分离的单体应用。前端 React SPA 通过 REST API + SSE 与后端通信，后端 Spring Boot WebFlux 提供响应式服务，PostgreSQL 存储数据（含 pgvector 向量检索）。

## 目录结构

```
DevKnowledge/
├── frontend/                    # React 前端 (Vite)
│   └── src/
│       ├── api/                 # API 客户端层（按模块拆分）
│       │   ├── client.ts        # HTTP 基础封装 + SSE 流式解析
│       │   ├── auth.ts          # 认证 API
│       │   ├── demos.ts         # Demo 生成 API（SSE）
│       │   ├── kb.ts            # 知识库 API
│       │   ├── knowledge.ts     # 知识搜索 API
│       │   ├── settings.ts      # AI 配置 API
│       │   ├── embedding.ts     # Embedding 配置 API
│       │   └── skills.ts        # Skills API
│       ├── components/          # UI 组件
│       │   ├── layout/          # Layout + Header + Sidebar
│       │   ├── knowledge/       # 框架卡片 + 搜索栏 + 链接卡片
│       │   └── skills/          # Skill 推荐卡片
│       ├── hooks/
│       │   └── useSSE.ts        # SSE 流式 Hook（AbortController）
│       ├── pages/               # 路由页面
│       │   ├── HomePage.tsx     # 首页（四模块入口）
│       │   ├── LoginPage.tsx    # 登录/注册
│       │   ├── KnowledgePage.tsx# 知识搜索 + 框架浏览
│       │   ├── DemoPage.tsx     # Demo 流式生成 + ReAct 可视化
│       │   ├── SkillsPage.tsx   # Skills 构建
│       │   ├── KbPage.tsx       # 知识库管理
│       │   ├── SettingsPage.tsx # 设置页（顶部导航切换）
│       │   └── settings/        # 设置子页面
│       │       ├── AiSettings.tsx       # AI 配置管理 + Token 图表
│       │       ├── EmbeddingSettings.tsx # Embedding 配置
│       │       └── StorageSettings.tsx   # 数据存储设置
│       ├── stores/              # Zustand 状态管理
│       │   ├── authStore.ts     # 认证状态（token + 用户信息）
│       │   └── notify.tsx       # 全局通知弹窗
│       ├── storage/
│       │   └── LocalActivityStorage.ts  # File System Access API
│       └── types/
│           └── api.ts           # TypeScript 类型定义
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
│       │   │   ├── KnowledgeController.java   # 框架/链接/全文搜索
│       │   │   ├── DemoController.java        # Demo SSE 生成 + 历史
│       │   │   └── KbController.java          # 知识库 CRUD + 文档上传
│       │   ├── service/         # 业务逻辑层
│       │   │   ├── AuthService.java           # 注册/登录/JWT
│       │   │   ├── AiConfigService.java       # AI 配置（多配置 + AES 加密）
│       │   │   ├── EmbeddingConfigService.java# Embedding 配置管理
│       │   │   ├── EmbeddingService.java      # /v1/embeddings 调用
│       │   │   ├── EmbeddingUsageService.java # Token 消耗统计
│       │   │   ├── KnowledgeService.java      # 全文搜索
│       │   │   ├── DemoService.java           # Demo 生成（ReAct Agent）
│       │   │   ├── KbService.java             # 知识库 + 向量化
│       │   │   ├── FileParserService.java     # 文件解析（txt/md/pdf/docx）
│       │   │   └── WebSearchService.java      # Bing 网页搜索
│       │   ├── service/ai/      # AI 适配层
│       │   │   ├── AiProviderAdapter.java     # 适配器接口
│       │   │   ├── AiProviderFactory.java     # 适配器工厂
│       │   │   ├── OpenAiCompatibleAdapter.java # OpenAI 兼容实现
│       │   │   ├── ReActAgent.java            # ReAct 多轮推理引擎
│       │   │   ├── DemoToolProvider.java      # 工具定义（search_links/search_kb）
│       │   │   ├── AiFunction.java            # 工具描述
│       │   │   ├── AiChunk.java               # 输出块
│       │   │   ├── AiChunkType.java           # 块类型枚举
│       │   │   └── ToolHandler.java           # 工具执行接口
│       │   ├── mapper/          # MyBatis Plus Mapper
│       │   │   ├── UserMapper.java
│       │   │   ├── UserAiConfigMapper.java
│       │   │   ├── UserEmbeddingConfigMapper.java
│       │   │   ├── FrameworkMapper.java
│       │   │   ├── KnowledgeLinkMapper.java   # 含 fullTextSearch 自定义 SQL
│       │   │   ├── DemoMapper.java
│       │   │   ├── KnowledgeBaseMapper.java
│       │   │   ├── KbDocumentMapper.java
│       │   │   ├── KbChunkMapper.java         # pgvector 余弦相似度检索
│       │   │   └── EmbeddingUsageMapper.java
│       │   ├── model/           # 实体类
│       │   │   ├── User.java / UserAiConfig.java / UserEmbeddingConfig.java
│       │   │   ├── Framework.java / KnowledgeLink.java
│       │   │   ├── KnowledgeBase.java / KbDocument.java / KbChunk.java
│       │   │   ├── Demo.java / EmbeddingUsage.java
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
│           └── db/migration/    # Flyway 迁移脚本（V1-V8）
│
├── docs/                        # 项目文档
├── 进度.md                      # 开发进度记录
└── api-docs.md                  # API 文档
```

## 核心模块关系

```
┌─────────────────────────────────────────────────────┐
│                    前端 (React)                       │
│  pages → api/client.ts → useSSE Hook → stores       │
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
| `frontend/src/hooks/useSSE.ts` | ★★★ | SSE Hook，Demo/ReAct 流式渲染的核心 |
| `frontend/src/stores/authStore.ts` | ★★☆ | 认证状态，token 管理 |
| `backend/.../security/JwtTokenProvider.java` | ★★★ | JWT 生成/解析，所有认证的基础 |
| `backend/.../security/AesUtil.java` | ★★★ | API Key 加密/脱敏 |
| `backend/.../service/ai/ReActAgent.java` | ★★★ | ReAct 多轮推理引擎 |
| `backend/.../service/ai/OpenAiCompatibleAdapter.java` | ★★★ | AI API 调用适配（支持多家服务商） |
| `backend/.../service/KbService.java` | ★★☆ | 知识库 + 向量化 + RAG |
| `backend/.../mapper/KbChunkMapper.java` | ★★☆ | pgvector 余弦相似度检索 SQL |
| `backend/resources/application.yml` | ★★☆ | 数据库/JWT/AI 配置 |
| `backend/resources/db/migration/` | ★★☆ | 数据库迁移（不可修改已提交的文件） |

## 数据流

### Demo 生成（SSE 流式）
```
前端 DemoPage → POST /api/demos/generate (SSE)
  → DemoController → DemoService
    → ReActAgent（多轮推理，最多 5 轮）
      → OpenAiCompatibleAdapter（流式调用 AI API）
      → DemoToolProvider（search_links / search_kb 工具执行）
    → 每个 AiChunk 实时推送到前端
  → doOnComplete 保存 Demo 记录
```

### 知识库 RAG 检索
```
文档上传 → FileParserService 解析 → 段落切分
  → EmbeddingService 批量向量化 → 存入 kb_chunks (pgvector)

Demo 生成时：
  → EmbeddingService 查询向量化
  → KbChunkMapper.searchByVector (余弦相似度 top-K)
  → 注入 system prompt + search_kb 工具
```

## 常见修改场景

| 场景 | 涉及文件 |
|------|---------|
| 新增 API 端点 | controller/ + service/ + 前端 api/ + pages/ |
| 新增数据库表 | model/ + mapper/ + `db/migration/V9__xxx.sql` |
| 修改 AI 调用逻辑 | `service/ai/` 目录 |
| 新增 ReAct 工具 | `DemoToolProvider.java` 注册 + 对应 Service |
| 修改页面 UI | `pages/` + `components/` |
| 修改认证逻辑 | `security/` + `SecurityConfig.java` |
| 新增 Embedding 模型 | `EmbeddingService.java` + 前端 `EmbeddingSettings.tsx` |
