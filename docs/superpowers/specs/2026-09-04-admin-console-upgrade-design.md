# 开发者后台升级设计文档

- 日期：2026-09-04
- 状态：已与用户确认（保留策略 / 错误上下文 / 用户列表 / 反馈管理四节均已批准）
- 范围：前后端 + 数据库（V21 迁移）

## 0. 背景与现状

V20 上线了请求可观测性（`request_traces` / `request_spans` / `error_reports` / `user_feedback`）与开发者后台（`AdminPage`）。两天的真实使用暴露四个问题：

| 痛点 | 现状根因 |
|---|---|
| 日志无限增长 | 无保留策略，当前 ~200 条/天（504 KB / 420 条） |
| 错误点不进去、没上下文 | `error_summary` 截断 2000 字符；`RequestTiming` 只留 `getMessage()`，堆栈丢弃；无详情接口 |
| 看不到用户列表 | 后台只有 `countUsers()` 一个数字 |
| 反馈管理粗糙 | 只读列表，无状态流转、无分页 |

另：`request_spans` 表建了但 0 条数据（埋点能力在 `RequestTiming.measureStage`，多数 Service 未接入）。

## 1. 数据保留策略（traces 留 14 天）

- **保留对象**：`request_traces`、`request_spans` 保留 14 天；`error_reports`、`user_feedback` 永久保留。
- **实现**：新增 `AdminHousekeepingService`，`@Scheduled(cron = "0 30 3 * * *")` 每天凌晨 3:30 执行：
  - `DELETE FROM request_traces WHERE created_at < NOW() - ($retentionDays || ' days')::interval`
  - `DELETE FROM request_spans WHERE created_at < 同上`
- **配置**：`application.yml` 新增 `observability.trace-retention-days: 14`，`@Value` 读取，改配置即可调整。
- **前置**：主类启用 `@EnableScheduling`（当前未启用）。
- **索引**：`request_spans.created_at` 无索引，V21 补 `idx_request_spans_created_at`。

## 2. 错误详情 + 链路追溯

### 2.1 数据层（V21）

`error_reports` 新增 `error_detail TEXT`（完整堆栈/上下文，可空）。仅新列，不回填存量。

### 2.2 后端采集补全

- **后端异常路径**：`RequestTiming.Snapshot` 增加携带 `Throwable`（或序列化后的堆栈字符串），`RequestObservabilityService.recordTrace()` 写 `error_reports` 时将完整堆栈序列化（标准 `StackTraceElement[]` 输出），过 `SensitiveDataSanitizer.sanitize` 后存入 `error_detail`。
- **前端上报路径**：`ClientErrorReportRequest` 加 `errorDetail` 字段；`RequestObservabilityService.reportError()` 同样 sanitize 后入库；前端 `errorReporting.ts` 的 `ClientErrorPayload` 加 `errorDetail`（采集 `error.stack`），走现有 sanitize 管道（截断上限独立，如 16000 字符）。

### 2.3 查询接口

- `GET /api/admin/errors/{id}`：单条错误完整详情（含 `errorDetail`）。
- `GET /api/admin/errors`：增加可选 `requestId` 参数过滤。
- `GET /api/admin/traces?requestId=xxx`：按 requestId 查请求链路（返回 trace + spans）。spans 展示遵循"有则显示"——本次不补 Service 埋点接入（避免范围膨胀）。

### 2.4 前端

错误列表每条可点击展开**详情抽屉**，三块内容：

1. 完整堆栈：等宽字体、纵向滚动区。
2. 关联请求链路：以 `error.requestId` 查 traces + spans，展示请求时间线（各阶段 stage / status / duration）。
3. 环境信息：userAgent / page / appVersion / environment / durationMs。

## 3. 用户列表

- **接口**：`GET /api/admin/users`，分页（复用 `AdminPageResponse<T>`）。
- **字段**：邮箱、昵称、注册时间、最近活跃时间（该用户最新一条 `request_traces.created_at`）、累计 Token（`demos` 表 token 汇总）、Demo 数（`demos` 计数）、反馈数（`user_feedback` 计数）。
- **SQL**：users 主查询 + 3 个标量子查询（当前 3 个用户，无性能问题；不做搜索）。
- **前端**：后台新增「用户」区块，分页表格；不做邮箱脱敏（管理员排查需要真实邮箱）。

## 4. 反馈状态流转 + 筛选

- **状态机**：`NEW`（新）→ `IN_PROGRESS`（处理中）→ `RESOLVED`（已解决），允许任意方向流转（含重新打开回 NEW）。仅这 3 个枚举值。
- **接口**：
  - `GET /api/admin/feedback`：改为分页（`page`/`size`），支持可选 `status` 过滤。
  - `PATCH /api/admin/feedback/{id}/status`：body `{ "status": "IN_PROGRESS" }`，非法值返回 400。
- **前端**：反馈区块顶部状态筛选 tab（全部 / 新 / 处理中 / 已解决）+ 分页；每条反馈带操作按钮（标记处理中 / 标记已解决 / 重新打开），按当前状态显示可用操作。
- **用户侧不动**：`FeedbackDialog` 提交后即结束，不做回复闭环。

## 5. 变更清单

### 数据库（V21__admin_console_upgrade.sql）

1. `ALTER TABLE error_reports ADD COLUMN error_detail TEXT`
2. `CREATE INDEX idx_request_spans_created_at ON request_spans(created_at DESC)`

### 后端

| 文件 | 变更 |
|---|---|
| `AdminHousekeepingService.java` 🆕 | 定时清理 traces/spans |
| 主类 | `@EnableScheduling` |
| `RequestTiming.java` | Snapshot 携带异常堆栈 |
| `RequestObservabilityService.java` | 写入 `error_detail`（后端 + 前端两条路径） |
| `ClientErrorReportRequest.java` | 加 `errorDetail` |
| `ErrorReport.java`（model） | 加 `errorDetail` |
| `AdminService.java` / `AdminController.java` | 错误详情、requestId 过滤、用户列表、反馈分页 + 状态流转 |
| `AdminMapper.java` | 对应 SQL |
| DTO | `AdminErrorResponse` 加 `errorDetail`；新增 `AdminUserResponse`、错误详情响应、反馈状态请求体 |
| `application.yml` | `observability.trace-retention-days: 14` |
| 测试 | AdminService（用户列表聚合、反馈状态校验、分页）、Housekeeping（删除范围） |

### 前端

| 文件 | 变更 |
|---|---|
| `api/admin.ts` | 错误详情、trace 链路、用户列表、反馈分页/状态接口 + 类型 |
| `pages/AdminPage.tsx` | 拆分：错误区块（点击详情抽屉）、用户新区块、反馈区块（tab + 分页 + 操作按钮） |
| `utils/errorReporting.ts` | `ClientErrorPayload` 加 `errorDetail`，采集 `error.stack` |
| 触发上报处（`ClientErrorReporter` 等） | 传堆栈 |

## 6. 测试与验收

- 后端：`mvn test`（新增 AdminService / HousekeepingService 测试）。
- 前端：`npm run build`。
- 手动走查：
  1. 触发一个后端异常 → 后台错误列表出现 → 点开详情抽屉可见完整堆栈 + 请求链路；
  2. 触发一个前端错误（如临时改代码抛错）→ 详情可见 `error.stack`；
  3. 用户列表显示 3 个用户（含 tour-test 测试号）且活跃时间/Token 数正确；
  4. 反馈：改状态 → tab 筛选正确 → 分页正常 → 非法状态值被 400 拒绝；
  5. 保留策略：临时把 `trace-retention-days` 设 0 重启 → traces 被清空（验证后改回 14）；或单测覆盖删除 SQL 条件即可。
- 文档同步：`api-docs.md`（新增/变更接口契约）、`进度.md`、`CODE_MAP.md`。

## 7. 边界情况

| 情况 | 处理 |
|---|---|
| 清理任务执行失败（DB 抖动） | catch + WARN 日志，次日重试（数据多留一天无碍） |
| 前端上报的 `errorDetail` 超长 | 前端截 16000 字符，后端 sanitize 管道兜底 |
| 详情抽屉查关联 trace 为空（trace 已被清理 / 前端错误无对应请求） | 显示「无关联请求记录」占位，不报错 |
| 反馈状态 PATCH 传入非法值 | 400 + 错误信息 |
| requestId 过滤无结果 | 空列表，前端正常展示空态 |
