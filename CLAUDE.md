# DevKnowledge — AI 开发指南

响应语言：中文

## 项目概述

全栈知识平台：知识搜索、AI Demo 生成（ReAct Agent）、知识库 RAG 向量检索、Wiki 知识图谱、Skills 构建。前后端分离——前端 React SPA 通过 REST + SSE 调后端；后端 Spring Boot WebFlux + PostgreSQL（全文检索 tsvector/GIN + pgvector）。

> `AGENTS.md` 是本文件的 symlink，供 Codex 等 Agent 读取同一套仓库规则。个人偏好写入 `CLAUDE.local.md`（已 gitignore），不要写进本文件。不要把一次性需求、调试记录、长流程说明写进根 `CLAUDE.md`。

---

## 目录速查

| 想做的事 | 去哪里 |
|---|---|
| 不确定模块 / 文件落点 | `CODE_MAP.md` |
| 前后端接口契约 | `api-docs.md` |
| 已有模块的设计文档 | `docs/superpowers/specs/` |
| 已有模块的实施计划 | `docs/superpowers/plans/` |
| 开发进度备忘 | `进度.md` |
| 数据库迁移 | `backend/src/main/resources/db/migration/`（当前最新 `V20`） |
| 运行时配置（库连接、JWT、路径） | `backend/src/main/resources/application.yml` |

---

## 分层上下文

根 `CLAUDE.md` 只保留全局规则和导航。进入具体目录后，优先读取该目录下的 `CLAUDE.md`：

| 目录 | 先读什么 | 适用场景 |
|---|---|---|
| `frontend/` | `frontend/CLAUDE.md` | 页面、组件、SSE、Zustand、API 客户端 |
| `backend/` | `backend/CLAUDE.md` | Controller / Service / AI / RAG / 安全 / Flyway |

没有目录级 `CLAUDE.md` 时，先读 `CODE_MAP.md`，再按任务命中的文件继续下钻。

---

## 硬性规则

1. **API Key 不明文**：存储必须走 AES-256-GCM；返回前端只允许脱敏。禁止把真实密钥、JWT secret、数据库密码写进文档或提交到 git。
2. **已提交的 Flyway 脚本不可修改**（校验和会失败）。表结构变更只能新增 `V20__xxx.sql` 及之后的脚本，并同步改 `model/` + `mapper/`。
3. **WebFlux 禁止阻塞调用**：MyBatis Plus 等阻塞 ORM 必须用 `Schedulers.boundedElastic()` 包装。
4. **未经用户确认不得 git commit**。
5. **不要改已 gitignore 的生成物**：`frontend/dist/`、`backend/target/`、`raw_files/`、`wiki_vault/`、`logs/`。`package-lock.json` 由 npm 生成，勿手改。

> 编码习惯：关键逻辑用简体中文注释。前端函数式组件 + Hooks + Tailwind 原子类。

---

## 执行约束（Karpathy Guidelines）

- 只做当前任务必要改动，不顺手重构、格式化或清理无关代码。
- 优先最小实现；不要为单次需求新增抽象、配置项或预留能力。
- 不确定会导致方向性错误时再提问；能通过读代码、文档或构建结果确认的，先自行确认。
- 每一处代码改动都应能对应到用户需求；改完按变更类型完成对应自检。

---

## 自检命令

```bash
# 前端（含 tsc）
cd frontend && npm install
cd frontend && npm run dev      # http://localhost:5173
cd frontend && npm run build    # 生产构建，声明前端通过必须跑这个

# 后端
cd backend && mvn spring-boot:run   # http://localhost:8080
cd backend && mvn compile
cd backend && mvn test              # 声明后端通过必须跑这个
```

环境：Node.js 18+、Java 17+、Maven 3.8+、PostgreSQL 16（需 `pgvector`）。CORS 已放行 `localhost:5173`。

构建验收规则：

- 只改文档 / 配置时，不需要跑前后端构建，但要说明未运行原因。
- 改 `frontend/**`（含 `.ts` / `.tsx`）必须 `cd frontend && npm run build` 通过。
- 改 `backend/**` 的 Java / 测试必须 `cd backend && mvn test` 通过；只动 `application.yml` 或 Flyway 时至少 `mvn compile`。
- 同时改前后端时分别验证，不得用一端结果代替另一端。

---

## 项目级 SKILL

本仓库目前没有入库的项目级 Skill（`.claude/` 已被 gitignore）。新功能若已有设计，先读 `docs/superpowers/specs/` 与对应 `docs/superpowers/plans/`，避免和既有方案打架。

需要长期复用的 Agent 流程时，再新增 Skill，并评估是否应从 gitignore 中放出 `.claude/skills/`。

---

## 技术栈（速览）

| 层 | 选型 |
|---|---|
| 前端 | React 19、TypeScript 5.8 strict、Vite 6.3、Tailwind CSS v4、Zustand 5、react-router-dom 7、Three.js |
| 后端 | Spring Boot 3.3 WebFlux、Java 17、MyBatis Plus 3.5.7、Flyway、JJWT 0.12.6、Lombok |
| 数据 | PostgreSQL 16（tsvector/GIN + pgvector） |
