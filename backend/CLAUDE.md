# backend — AI 开发指南

先读根 `CLAUDE.md` 的硬性规则，再读本文件。模块落点以 `CODE_MAP.md` 为准。

## 适用场景

Controller、Service、Mapper、AI 适配层、RAG、安全、Flyway。

## 约定

- 分层：`controller/` → `service/` → `mapper/` → PostgreSQL。DTO 放 `dto/`，实体放 `model/`。
- WebFlux 线程里禁止阻塞 IO。MyBatis Plus、文件解析、Jieba 等必须 `subscribeOn(Schedulers.boundedElastic())`。
- API Key 只经 `security/AesUtil.java` 加解密；接口返回脱敏值。
- 表结构变更：新增 `src/main/resources/db/migration/V21__xxx.sql`（当前最新是 `V20`），**禁止改已提交脚本**，并同步 `model/` + `mapper/`。
- 改 AI 调用：`service/ai/`（Adapter / ReActAgent / AiChunk）。新增 ReAct 工具：在 `DemoToolProvider.java` 注册，并接到对应 Service。
- 改 RAG：`KbService.java` + `KbChunkMapper.java` + `JiebaSegmenter.java`（查询侧与入库侧共用同一套分词）。
- 接口路径与字段以根目录 `api-docs.md` 为准；改了契约要同步文档。

## 自检

改 Java / 测试后必须 `mvn test`。只改 `application.yml` 或新 Flyway 脚本时至少 `mvn compile`。
