# DevKnowledge 接口文档

本接口文档为 DevKnowledge 前后端对接提供标准。
**Base URL:** `http://localhost:8080`
**认证方式:** 接口默认需要携带 HTTP Header `Authorization: Bearer <token>`，除部分公共接口（如获取框架、获取链接等）外。

---

## 1. 认证接口 (Auth)

### 1.1 注册
- **URL:** `/api/auth/register`
- **Method:** `POST`
- **Auth Required:** No
- **Request Body:**
  ```json
  {
    "email": "user@example.com",
    "password": "password123",
    "displayName": "User Name"
  }
  ```
- **Response (200 OK):**
  ```json
  {
    "token": "jwt-token-string",
    "user": {
      "id": "uuid",
      "email": "user@example.com",
      "displayName": "User Name"
    }
  }
  ```

### 1.2 登录
- **URL:** `/api/auth/login`
- **Method:** `POST`
- **Auth Required:** No
- **Request Body:**
  ```json
  {
    "email": "user@example.com",
    "password": "password123"
  }
  ```
- **Response (200 OK):** 同注册接口返回。

### 1.3 刷新 Token
- **URL:** `/api/auth/refresh`
- **Method:** `POST`
- **Auth Required:** Yes
- **Response (200 OK):**
  ```json
  {
    "token": "new-jwt-token-string"
  }
  ```

---

## 2. 用户 AI 配置接口 (Settings)

### 2.1 获取当前配置
- **URL:** `/api/user/ai-config`
- **Method:** `GET`
- **Auth Required:** Yes
- **Response (200 OK):**
  ```json
  {
    "provider": "openai",
    "apiKey": "sk-...1234", // 脱敏
    "baseUrl": "https://api.openai.com/v1",
    "model": "gpt-4o",
    "maxTokens": 4096
  }
  ```

### 2.2 更新 AI 配置
- **URL:** `/api/user/ai-config`
- **Method:** `PUT`
- **Auth Required:** Yes
- **Request Body:** (apiKey 如果为脱敏状态或为空，则表示不修改)
  ```json
  {
    "provider": "openai",
    "apiKey": "sk-new-key...",
    "baseUrl": "https://api.openai.com/v1",
    "model": "gpt-4o",
    "maxTokens": 4096
  }
  ```
- **Response (200 OK):** 返回更新后的配置。

### 2.3 测试配置是否可用
- **URL:** `/api/user/ai-config/test`
- **Method:** `POST`
- **Auth Required:** Yes
- **Response (200 OK):**
  ```json
  {
    "success": true,
    "message": "Connection successful"
  }
  ```

### 2.4 获取支持的 Provider 列表
- **URL:** `/api/providers`
- **Method:** `GET`
- **Auth Required:** No
- **Response (200 OK):**
  ```json
  [
    {
      "id": "openai",
      "name": "OpenAI",
      "defaultBaseUrl": "https://api.openai.com/v1"
    },
    {
      "id": "anthropic",
      "name": "Anthropic Claude",
      "defaultBaseUrl": "https://api.anthropic.com"
    },
    {
      "id": "custom",
      "name": "Custom (OpenAI Compatible)",
      "defaultBaseUrl": ""
    }
  ]
  ```

---

## 3. 知识模块接口 (Knowledge)

### 3.1 获取框架列表
- **URL:** `/api/frameworks`
- **Method:** `GET`
- **Auth Required:** No
- **Response (200 OK):**
  ```json
  [
    {
      "id": "uuid",
      "name": "React",
      "slug": "react",
      "baseUrl": "https://react.dev",
      "iconUrl": "/icons/react.svg",
      "description": "The library for web and native user interfaces",
      "category": "frontend"
    }
  ]
  ```

### 3.2 获取框架下的文档链接
- **URL:** `/api/frameworks/{slug}/links`
- **Method:** `GET`
- **Auth Required:** No
- **Response (200 OK):**
  ```json
  [
    {
      "id": "uuid",
      "frameworkId": "uuid",
      "title": "useEffect Hook",
      "url": "https://react.dev/reference/react/useEffect",
      "anchor": "",
      "description": "useEffect is a React Hook that lets you synchronize a component with an external system.",
      "tags": ["hook", "effect", "lifecycle"]
    }
  ]
  ```

### 3.3 全文搜索链接
- **URL:** `/api/links/search`
- **Method:** `GET`
- **Query Params:** `q` (关键词)
- **Auth Required:** No
- **Response (200 OK):** 返回包含 `title` / `description` 摘要等同于链接列表的格式。

---

## 4. Demo 生成接口 (Demos)

### 4.1 流式生成 Demo
- **URL:** `/api/demos/generate`
- **Method:** `POST`
- **Auth Required:** Yes
- **Request Body:**
  ```json
  {
    "prompt": "如何用 React useEffect 发起请求",
    "frameworkId": "uuid-optional",
    "kbId": "uuid-optional"
  }
  ```
- **Response:** `text/event-stream` (SSE)
  ```text
  event: metadata
  data: {"language": "typescript"}

  event: code
  data: "import React "

  event: code
  data: "from 'react';"

  event: explanation
  data: "首先引入"

  event: done
  data: {"id": "new-demo-uuid"}
  ```

### 4.2 获取历史 Demo 列表
- **URL:** `/api/demos`
- **Method:** `GET`
- **Auth Required:** Yes
- **Response (200 OK):**
  ```json
  [
    {
      "id": "uuid",
      "title": "React useEffect 请求示例",
      "prompt": "如何用 React useEffect 发起请求",
      "language": "typescript",
      "createdAt": "2026-05-01T14:00:00Z"
    }
  ]
  ```

### 4.3 获取单个 Demo
- **URL:** `/api/demos/{id}`
- **Method:** `GET`
- **Auth Required:** Yes
- **Response (200 OK):**
  ```json
  {
    "id": "uuid",
    "title": "React useEffect 请求示例",
    "prompt": "如何用 React...",
    "codeContent": "import React...",
    "explanation": "首先引入...",
    "language": "typescript"
  }
  ```

### 4.4 删除 Demo
- **URL:** `/api/demos/{id}`
- **Method:** `DELETE`
- **Auth Required:** Yes

---

## 5. Skills 接口 (Skills)

### 5.1 从自然语言提取 Skill (SSE 流式或长轮询，推荐SSE以便展示进度)
- **URL:** `/api/skills/extract`
- **Method:** `POST`
- **Auth Required:** Yes
- **Request Body:**
  ```json
  {
    "description": "帮我提取一个用来创建标准 React 组件的技能..."
  }
  ```
- **Response:** 返回解析并保存好的 Skill 对象。

### 5.2 获取/编辑/删除 Skills
- **URL:** `/api/skills`, `/api/skills/{id}`
- **Method:** `GET`, `PUT`, `DELETE`
- **Auth Required:** Yes

### 5.3 导出为 Claude Code MD 格式
- **URL:** `/api/skills/{id}/export`
- **Method:** `POST`
- **Auth Required:** Yes
- **Response (200 OK):** 返回生成的 markdown 内容字符串。

### 5.4 下载 MD 文件
- **URL:** `/api/skills/{id}/export/download`
- **Method:** `GET`
- **Auth Required:** Yes
- **Response:** `text/markdown` 文件下载。

---

## 6. 知识库接口 (Knowledge Base / RAG)

### 6.1 增删改查知识库
- **URL:** `/api/kb`, `/api/kb/{id}`
- **Method:** `GET`, `POST`, `DELETE`
- **Auth Required:** Yes

### 6.2 文档上传与管理
- **URL:** `/api/kb/{id}/documents`
- **Method:** `POST`
- **Auth Required:** Yes
- **Content-Type:** `multipart/form-data` (file)
- **Response (200 OK):** 处理成功。

### 6.3 语义搜索
- **URL:** `/api/kb/{id}/search`
- **Method:** `GET`
- **Query Params:** `q` (关键词)
- **Auth Required:** Yes
- **Response (200 OK):** 返回匹配的文档片段列表。

---

## 7. 错误上报与用户反馈

### 7.1 前端错误上报
- **URL:** `/api/telemetry/errors`
- **Method:** `POST`
- **Auth Required:** No（登录用户会自动关联 userId）
- **说明:** 只接收脱敏错误摘要和运行环境，不接收 Prompt、API Key、密码或完整 AI 输出。

### 7.2 用户意见反馈
- **URL:** `/api/feedback`
- **Method:** `POST`
- **Auth Required:** No
- **Request Body:**
  ```json
  {
    "feedbackType": "FEATURE",
    "content": "希望支持更多代码框架",
    "contact": "user@example.com",
    "page": "/demos",
    "requestId": "request-uuid"
  }
  ```

---

## 8. 开发者后台接口

后台接口需要携带登录 Token，且 Token 对应邮箱必须配置在
`DEVKNOWLEDGE_ADMIN_EMAILS` 白名单中；普通用户不会看到后台导航。

### 8.1 概览指标
- **URL:** `/api/admin/overview`
- **Method:** `GET`
- **Auth Required:** Admin
- **返回:** 用户数、累计 Token、请求数、成功率、平均/P95 耗时、错误数、反馈数。

### 8.2 错误记录
- **URL:** `/api/admin/errors?limit=50&requestId=xxx`（requestId 可选，按请求 ID 过滤）
- **Method:** `GET`
- **Auth Required:** Admin
- **返回:** 最近的脱敏错误摘要和请求上下文（不含完整堆栈，列表响应保持轻量）。

### 8.2.1 错误详情
- **URL:** `/api/admin/errors/{id}`
- **Method:** `GET`
- **Auth Required:** Admin
- **返回:** 单条错误完整信息，含 `errorDetail`（脱敏后的完整堆栈，可空）。

### 8.3 请求耗时记录
- **URL:** `/api/admin/traces?page=1&size=20`
- **Method:** `GET`
- **Auth Required:** Admin
- **返回:** 分页请求记录，包含状态、总耗时、SSE 首事件/首文本耗时、当前页、总条数和总页数。

### 8.3.1 请求链路详情
- **URL:** `/api/admin/traces/detail?requestId=xxx`
- **Method:** `GET`
- **Auth Required:** Admin
- **返回:** `{ trace: 请求记录|null, spans: [{stage, status, durationMs, createdAt}] }`，用于错误详情抽屉的链路追溯。

### 8.4 用户反馈
- **URL:** `/api/admin/feedback?page=1&size=20&status=NEW`（status 可选：NEW / IN_PROGRESS / RESOLVED）
- **Method:** `GET`
- **Auth Required:** Admin
- **返回:** 分页反馈记录（AdminPageResponse 结构），支持状态筛选。

### 8.4.1 反馈状态流转
- **URL:** `/api/admin/feedback/{id}/status`
- **Method:** `PATCH`
- **Auth Required:** Admin
- **Body:** `{ "status": "NEW | IN_PROGRESS | RESOLVED" }`
- **返回:** 200 成功；404 反馈不存在；400 状态值非法。

### 8.4.2 用户列表
- **URL:** `/api/admin/users?page=1&size=20`
- **Method:** `GET`
- **Auth Required:** Admin
- **返回:** 分页用户列表，含邮箱、昵称、注册时间、最近活跃时间、累计 Token、Demo 数、反馈数。

### 8.5 请求链路 Header
- `X-Request-Id`: 客户端生成合法 UUID 时由服务端复用，否则由 WebFilter 生成。
- `X-Client-Version`: 前端版本，默认值为 `dev`。
- 服务端响应会返回 `X-Request-Id`，便于错误反馈和日志关联。

### 8.6 邮件与后台配置
- `DEVKNOWLEDGE_ADMIN_EMAILS`: 开发者后台管理员邮箱，多个邮箱用逗号分隔。
- `DEVKNOWLEDGE_DEVELOPER_EMAIL`: 错误和用户反馈的收件邮箱。
- `MAIL_HOST` / `MAIL_PORT` / `MAIL_USERNAME` / `MAIL_PASSWORD` / `MAIL_FROM`: SMTP 配置。
- 未配置 SMTP 或开发者邮箱时，记录仍会保存到数据库，但不会发送邮件。
