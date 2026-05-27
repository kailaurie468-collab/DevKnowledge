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
