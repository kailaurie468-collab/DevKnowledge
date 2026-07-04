# DevKnowledge

## 项目概述
全栈知识平台，支持知识搜索、AI Demo 生成（ReAct Agent）、知识库 RAG 向量检索、Skills 构建。

## 技术栈

**前端**
- React 19 + TypeScript 5.8（strict 模式）
- Vite 6.3 + Tailwind CSS v4
- Zustand 5.0 状态管理
- react-router-dom 7.6 路由
- Three.js + @react-three/fiber（粒子特效）
- eventsource-parser（SSE 流式解析）

**后端**
- Spring Boot 3.3 WebFlux（响应式）
- Java 17
- MyBatis Plus 3.5.7
- PostgreSQL 16（全文搜索 tsvector/GIN + pgvector 向量检索）
- Flyway 数据库迁移
- JJWT 0.12.6 认证
- Lombok

## 常用命令

```bash
# 前端
cd frontend && npm install    # 安装依赖
cd frontend && npm run dev    # 启动开发服务器 (port 5173)
cd frontend && npm run build  # 生产构建
cd frontend && npm run lint   # 代码检查

# 后端
cd backend && mvn spring-boot:run           # 启动后端 (port 8080)
cd backend && mvn compile                   # 编译
cd backend && mvn dependency:resolve        # 解析依赖
cd backend && mvn test                      # 运行测试
```

**访问地址：**
- 前端开发服务器：http://localhost:5173
- 后端 API 服务：http://localhost:8080

**环境要求：**
- Node.js 18+
- Java 17+
- Maven 3.8+
- PostgreSQL 16（需安装 pgvector 扩展）

**数据库配置：**
在 `backend/src/main/resources/application.yml` 中配置：
- `spring.datasource.url` - PostgreSQL 连接地址
- `spring.datasource.username` - 数据库用户名
- `spring.datasource.password` - 数据库密码
- `jwt.secret` - JWT 密钥（Base64 编码）

## 代码规范

- 编码时附带关键注释（中文）
- 前端：函数式组件 + Hooks，Tailwind CSS 原子类
- 后端：响应式编程（Mono/Flux），阻塞 ORM 用 `Schedulers.boundedElastic()` 包装
- API Key 等敏感信息使用 AES-256-GCM 加密存储
- 数据库变更必须通过 Flyway 迁移脚本（`resources/db/migration/`）

## 项目结构

当需要定位模块、修改代码或了解某个功能的实现位置时，请先查阅 [CODE_MAP.md](CODE_MAP.md) 快速定位相关文件和模块边界，避免遗漏关联文件。

## 重要约束

- API Key 永远不明文存储或返回前端（AES 加密 + 脱敏展示）
- 数据库迁移文件一旦提交不可修改（Flyway 校验和机制）
- WebFlux 环境禁止阻塞调用，MyBatis Plus 必须用 `Schedulers.boundedElastic()` 包装
- 前端开发服务器端口 5173，后端 8080，CORS 已配置
