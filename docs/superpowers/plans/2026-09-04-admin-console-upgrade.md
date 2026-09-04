# 开发者后台升级实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 开发者后台从只读概览升级为可用的运维台：traces 14 天自动清理、错误可点开看完整堆栈+请求链路、用户列表、反馈状态流转。

**Architecture:** 后端围绕现有 `AdminController`/`AdminService`/`AdminMapper` 注解 SQL 模式扩展（新增 housekeeping 定时任务、错误详情/用户列表/反馈状态接口）；前端升级 `AdminPage.tsx`（错误详情抽屉、用户表格、反馈 tab+分页+操作）；数据库走 V21 迁移（`error_reports.error_detail` 列 + `request_spans` 索引）。

**Tech Stack:** Spring Boot 3.3 WebFlux + MyBatis 注解 SQL + Flyway V21；React 19 + TS strict + Tailwind v4。

**Spec:** `docs/superpowers/specs/2026-09-04-admin-console-upgrade-design.md`（执行者应同时读它）

## Global Constraints

- 响应与注释语言：简体中文。关键逻辑用简体中文注释（仓库规则）。
- **Flyway**：只能新增 `V21__admin_console_upgrade.sql`，禁止改 V1-V20。表结构变更同步 `model/` + `mapper/`。
- **WebFlux**：所有 MyBatis 调用必须 `subscribeOn(Schedulers.boundedElastic())`（AdminService 现有模式照抄）。
- **脱敏**：所有入库的错误内容必须过 `SensitiveDataSanitizer`；API Key/JWT/密码不得明文入库。
- **后端验证**：`cd backend && mvn test` 必须通过。**前端验证**：`cd frontend && npm run build` 必须通过。
- **测试风格**：纯 JUnit 5 + AssertJ，无 mock 框架（现有测试均如此，不引入新依赖）。
- **git 提交**：仓库规则「未经用户确认不得 git commit」。本计划用户已说「你去完成吧」——按引导功能先例，跳过所有 commit 步骤，改完统一交给用户决定。
- **Snapshot 是 record**：`RequestTiming.Snapshot` 加字段会破坏现有构造调用，`snapshot()` 内部是唯一构造点（已核实），改动收敛在 RequestTiming + RequestObservabilityService 两个文件。
- **SensitiveDataSanitizer.MAX_LENGTH 固定 2000**：`error_detail` 需要 16000 上限，新增 `sanitizeDetail(String)` 重载，不动现有 `sanitize`（已有测试依赖 2000 行为）。
- 后台鉴权复用现有 `adminAccessService.isAdmin(authorization)` 模式，每个新端点同样包一层 403。

---

### Task 1: V21 迁移 + ErrorReport 模型加 errorDetail

**Files:**
- Create: `backend/src/main/resources/db/migration/V21__admin_console_upgrade.sql`
- Modify: `backend/src/main/java/com/devknowledge/model/ErrorReport.java`
- Modify: `backend/src/main/java/com/devknowledge/dto/ClientErrorReportRequest.java`
- Modify: `backend/src/main/java/com/devknowledge/dto/AdminErrorResponse.java`

**Interfaces:**
- Consumes: 无。
- Produces: `error_reports.error_detail TEXT` 列；`ErrorReport.errorDetail` 字段（MyBatis Plus 自动驼峰映射 `error_detail`）；`ClientErrorReportRequest.errorDetail`（`@Size(max=16000)`）；`AdminErrorResponse.errorDetail`（供 Task 3 查询映射）。

- [ ] **Step 1: 写 V21 迁移脚本**

`backend/src/main/resources/db/migration/V21__admin_console_upgrade.sql`：

```sql
-- V21: 开发者后台升级
-- error_reports 新增完整堆栈列；request_spans 补 created_at 索引（保留策略清理用）

ALTER TABLE error_reports ADD COLUMN error_detail TEXT;

CREATE INDEX idx_request_spans_created_at ON request_spans(created_at DESC);
```

- [ ] **Step 2: ErrorReport 模型加字段**

`model/ErrorReport.java` 在 `errorSummary` 字段后加：

```java
    /** 完整堆栈/上下文（脱敏后），列表页不返回此字段 */
    private String errorDetail;
```

- [ ] **Step 3: ClientErrorReportRequest 加字段**

`dto/ClientErrorReportRequest.java` 在 `errorSummary` 字段后加：

```java
    @Size(max = 16000)
    private String errorDetail;
```

- [ ] **Step 4: AdminErrorResponse 加字段**

`dto/AdminErrorResponse.java` 在 `errorSummary` 字段后加：

```java
    private String errorDetail;
```

- [ ] **Step 5: 编译验证**

```bash
cd /Users/miaochencheng/IdeaProjects/DevKnowledge/backend && mvn compile -q
```

Expected: BUILD SUCCESS。

---

### Task 2: 后端采集完整堆栈（RequestTiming + Observability + Sanitizer）

**Files:**
- Modify: `backend/src/main/java/com/devknowledge/service/RequestTiming.java:76-81,108-143,188-204`（markError 存堆栈、Snapshot 加 errorStackTrace）
- Modify: `backend/src/main/java/com/devknowledge/service/SensitiveDataSanitizer.java`（新增 sanitizeDetail）
- Modify: `backend/src/main/java/com/devknowledge/service/RequestObservabilityService.java`（两条入库路径写 errorDetail）
- Test: `backend/src/test/java/com/devknowledge/service/SensitiveDataSanitizerTest.java`（新增用例）

**Interfaces:**
- Consumes: Task 1 的 `ErrorReport.errorDetail`。
- Produces: `SensitiveDataSanitizer.sanitizeDetail(String value): String`（脱敏 + 16000 截断，空值返回 null）；`RequestTiming.Snapshot` 新字段 `errorStackTrace`（String，最后一个字段之前插入）。

- [ ] **Step 1: 写失败测试**

`SensitiveDataSanitizerTest.java` 追加：

```java
    @Test
    @DisplayName("sanitizeDetail 保留堆栈换行并清理凭证")
    void sanitizeDetailKeepsNewlines() {
        String stack = "java.lang.IllegalStateException: Bearer abc.def.ghi\n"
                + "\tat com.devknowledge.service.DemoService.generate(DemoService.java:97)\n"
                + "\tat java.base/java.lang.Thread.run(Thread.java:833)";

        String result = SensitiveDataSanitizer.sanitizeDetail(stack);

        assertThat(result).contains("\n");
        assertThat(result).doesNotContain("abc.def.ghi");
        assertThat(result).contains("[REDACTED]");
    }

    @Test
    @DisplayName("sanitizeDetail 截断到 16000 且空值返回 null")
    void sanitizeDetailLimitsLengthAndNull() {
        assertThat(SensitiveDataSanitizer.sanitizeDetail(null)).isNull();
        assertThat(SensitiveDataSanitizer.sanitizeDetail("x".repeat(20000))).hasSize(16003);
    }
```

- [ ] **Step 2: 跑测试确认失败**

```bash
cd /Users/miaochencheng/IdeaProjects/DevKnowledge/backend && mvn test -q -Dtest=SensitiveDataSanitizerTest
```

Expected: 编译失败（`sanitizeDetail` 不存在）。

- [ ] **Step 3: 实现 sanitizeDetail**

`SensitiveDataSanitizer.java` 修改两处：

① 类常量区加：

```java
    /** 完整堆栈的最大长度，比摘要宽松以保留完整调用链 */
    private static final int MAX_DETAIL_LENGTH = 16000;
```

② 类尾加方法：

```java
    /**
     * 脱敏完整堆栈。与 sanitize 的区别：保留换行（堆栈逐行可读）、上限 16000、
     * 空值返回 null（error_detail 列允许 NULL，代表无堆栈）。
     */
    public static String sanitizeDetail(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String sanitized = BEARER_PATTERN.matcher(value).replaceAll("Bearer [REDACTED]");
        sanitized = KEY_VALUE_PATTERN.matcher(sanitized).replaceAll("$1=[REDACTED]");
        sanitized = JSON_KEY_PATTERN.matcher(sanitized).replaceAll("$1[REDACTED]$2");
        return sanitized.length() <= MAX_DETAIL_LENGTH
                ? sanitized
                : sanitized.substring(0, MAX_DETAIL_LENGTH) + "...";
    }
```

- [ ] **Step 4: 跑测试确认通过**

```bash
mvn test -q -Dtest=SensitiveDataSanitizerTest
```

Expected: PASS（新旧用例全过）。

- [ ] **Step 5: RequestTiming 携带堆栈**

`RequestTiming.java` 三处修改：

① 加私有字段（`logicalErrorMessage` 之后）：

```java
    private volatile String logicalErrorStackTrace;
```

② `markError` 改为（保留原逻辑，追加堆栈记录）：

```java
    /**
     * 记录异常，供最终快照统一决定 ERROR 或 TIMEOUT 状态。
     */
    public void markError(Throwable error) {
        if (error != null && logicalErrorCode == null) {
            logicalErrorCode = error.getClass().getSimpleName();
            logicalErrorMessage = error.getMessage();
            logicalErrorStackTrace = renderStackTrace(error);
        }
    }

    /** 异常堆栈序列化为多行字符串（含 cause 链，最多 5 层防超长） */
    private static String renderStackTrace(Throwable error) {
        StringBuilder sb = new StringBuilder();
        Throwable current = error;
        int depth = 0;
        while (current != null && depth < 5) {
            if (depth > 0) {
                sb.append("\nCaused by: ");
            }
            sb.append(current.getClass().getName());
            sb.append(": ").append(current.getMessage()).append('\n');
            for (StackTraceElement element : current.getStackTrace()) {
                sb.append("\tat ").append(element.toString()).append('\n');
            }
            current = current.getCause();
            depth++;
        }
        return sb.toString();
    }
```

③ `snapshot()` 方法改两处——开头取值处（`String errorMessage = logicalErrorMessage;` 之后）：

```java
        String errorStackTrace = logicalErrorStackTrace;
        if (error != null) {
            errorStackTrace = renderStackTrace(error);
        }
```

以及 `return new Snapshot(...)` 参数列表中，`errorMessage` 之后插入一行 `errorStackTrace,`。

④ `Snapshot` record 在 `errorMessage` 与 `userAgent` 之间加字段：

```java
            String errorStackTrace,
```

- [ ] **Step 6: RequestObservabilityService 写 errorDetail**

`RequestObservabilityService.java` 三处修改：

① `recordTrace` 里 `ErrorReport report = new ErrorReport();` 块中，`report.setErrorSummary(...)` 行后加：

```java
                    report.setErrorDetail(SensitiveDataSanitizer.sanitizeDetail(snapshot.errorStackTrace()));
```

② `reportError` 方法中，`report.setErrorSummary(...)` 行后加：

```java
        report.setErrorDetail(SensitiveDataSanitizer.sanitizeDetail(report.getErrorDetail()));
```

③ `TelemetryController` 组装 ErrorReport 的地方需要把 `errorDetail` 从 DTO 拷过去——先执行下面命令定位：

```bash
grep -n "setErrorSummary" backend/src/main/java/com/devknowledge/controller/TelemetryController.java
```

在该 `setErrorSummary` 调用行后加：

```java
            report.setErrorDetail(request.getErrorDetail());
```

（TelemetryController 是把 `ClientErrorReportRequest` 映射到 `ErrorReport` 的地方；具体变量名以实际代码为准。）

- [ ] **Step 7: 编译 + 全量测试**

```bash
mvn test -q
```

Expected: BUILD SUCCESS，无测试回归。

---

### Task 3: 后台查询接口扩展（错误详情/链路/用户列表/反馈流转）

**Files:**
- Modify: `backend/src/main/java/com/devknowledge/mapper/AdminMapper.java`
- Modify: `backend/src/main/java/com/devknowledge/dto/AdminFeedbackResponse.java`（如缺 status 已有则不动）
- Create: `backend/src/main/java/com/devknowledge/dto/AdminUserResponse.java`
- Create: `backend/src/main/java/com/devknowledge/dto/FeedbackStatusRequest.java`
- Create: `backend/src/main/java/com/devknowledge/dto/AdminTraceDetailResponse.java`
- Modify: `backend/src/main/java/com/devknowledge/service/AdminService.java`
- Modify: `backend/src/main/java/com/devknowledge/controller/AdminController.java`
- Create: `backend/src/main/java/com/devknowledge/mapper/FeedbackStatusMapper.java`（或并入 AdminMapper，见 Step 1 决定）

**Interfaces:**
- Consumes: Task 1 的 `error_detail` 列；现有 `AdminPageResponse<T>`、`AdminAccessService.isAdmin`。
- Produces（前端 Task 5 消费的契约，字段名必须一致）：
  - `GET /api/admin/errors/{id}` → `AdminErrorResponse`（含 `errorDetail`）
  - `GET /api/admin/errors?requestId=xxx` → `List<AdminErrorResponse>`
  - `GET /api/admin/traces/detail?requestId=xxx` → `AdminTraceDetailResponse { trace: AdminRequestTraceResponse|null, spans: List<AdminSpanResponse> }`
  - `GET /api/admin/users?page&size` → `AdminPageResponse<AdminUserResponse>`，字段：`id/email/displayName/createdAt/lastActiveAt/totalTokens/demoCount/feedbackCount`
  - `GET /api/admin/feedback?page&size&status` → `AdminPageResponse<AdminFeedbackResponse>`（**破坏性变更**：原来是数组）
  - `PATCH /api/admin/feedback/{id}/status`，body `{"status":"NEW|IN_PROGRESS|RESOLVED"}`，非法值 400

- [ ] **Step 1: AdminMapper 加查询**

`AdminMapper.java` 追加（保持现有注解 SQL 风格；`listFeedback` 改分页版，原方法删除，同文件 `countFeedback` 保留）：

```java
    /** 错误详情（含完整堆栈） */
    @Select("""
            SELECT id, request_id AS requestId, user_id AS userId, source, stage,
                   error_type AS errorType, error_summary AS errorSummary,
                   error_detail AS errorDetail,
                   method, path, page, app_version AS appVersion,
                   user_agent AS userAgent, environment,
                   duration_ms AS durationMs, created_at AS createdAt
            FROM error_reports
            WHERE id = #{id}
            """)
    AdminErrorResponse findErrorById(@Param("id") String id);

    /** 按 requestId 过滤错误列表 */
    @Select("""
            SELECT id, request_id AS requestId, user_id AS userId, source, stage,
                   error_type AS errorType, error_summary AS errorSummary,
                   error_detail AS errorDetail,
                   method, path, page, app_version AS appVersion,
                   user_agent AS userAgent, environment,
                   duration_ms AS durationMs, created_at AS createdAt
            FROM error_reports
            WHERE request_id = #{requestId}
            ORDER BY created_at DESC
            """)
    List<AdminErrorResponse> listErrorsByRequestId(@Param("requestId") String requestId);

    /** requestId 对应的请求记录（链路追溯） */
    @Select("""
            SELECT request_id AS requestId, method, path,
                   status_code AS statusCode, outcome,
                   total_ms AS totalMs, first_event_ms AS firstEventMs,
                   first_text_ms AS firstTextMs, created_at AS createdAt
            FROM request_traces
            WHERE request_id = #{requestId}
            ORDER BY created_at DESC
            LIMIT 1
            """)
    AdminRequestTraceResponse findTraceByRequestId(@Param("requestId") String requestId);

    /** requestId 对应的阶段耗时（spans） */
    @Select("""
            SELECT stage, status, duration_ms AS durationMs, created_at AS createdAt
            FROM request_spans
            WHERE request_id = #{requestId}
            ORDER BY created_at ASC
            """)
    List<AdminSpanResponse> listSpansByRequestId(@Param("requestId") String requestId);

    /** 用户列表（含活跃时间与用量聚合；标量子查询，当前用户量小无性能问题） */
    @Select("""
            SELECT u.id, u.email, u.display_name AS displayName, u.created_at AS createdAt,
                   (SELECT MAX(t.created_at) FROM request_traces t WHERE t.user_id = u.id) AS lastActiveAt,
                   (SELECT COALESCE(SUM(COALESCE(d.tokens_used, 0)), 0) FROM demos d WHERE d.user_id = u.id) AS totalTokens,
                   (SELECT COUNT(*) FROM demos d WHERE d.user_id = u.id) AS demoCount,
                   (SELECT COUNT(*) FROM user_feedback f WHERE f.user_id = u.id) AS feedbackCount
            FROM users u
            ORDER BY u.created_at DESC
            LIMIT #{size} OFFSET #{offset}
            """)
    List<AdminUserResponse> listUsers(@Param("offset") int offset, @Param("size") int size);

    @Select("SELECT COUNT(*) FROM users")
    long countUsersForPage();

    /** 反馈分页 + 可选状态过滤（status 为 null 时不过滤） */
    @Select("""
            <script>
            SELECT id, request_id AS requestId, user_id AS userId,
                   feedback_type AS feedbackType, content, contact, page,
                   status, created_at AS createdAt
            FROM user_feedback
            <where>
                <if test="status != null and status != ''">status = #{status}</if>
            </where>
            ORDER BY created_at DESC
            LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<AdminFeedbackResponse> listFeedbackPage(
            @Param("status") String status,
            @Param("offset") int offset,
            @Param("size") int size);

    @Select("""
            <script>
            SELECT COUNT(*) FROM user_feedback
            <where>
                <if test="status != null and status != ''">status = #{status}</if>
            </where>
            </script>
            """)
    long countFeedbackByStatus(@Param("status") String status);

    /** 反馈状态流转 */
    @Update("UPDATE user_feedback SET status = #{status} WHERE id = #{id}::uuid")
    int updateFeedbackStatus(@Param("id") String id, @Param("status") String status);
```

注意：文件顶部 import 加 `org.apache.ibatis.annotations.Update`。

- [ ] **Step 2: 新 DTO**

`dto/AdminUserResponse.java`：

```java
package com.devknowledge.dto;

import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * 后台用户列表行。
 */
@Data
public class AdminUserResponse {
    private UUID id;
    private String email;
    private String displayName;
    private Instant createdAt;
    private Instant lastActiveAt;
    private Long totalTokens;
    private Long demoCount;
    private Long feedbackCount;
}
```

`dto/AdminSpanResponse.java`：

```java
package com.devknowledge.dto;

import lombok.Data;

import java.time.Instant;

/**
 * 请求阶段耗时（链路追溯用）。
 */
@Data
public class AdminSpanResponse {
    private String stage;
    private String status;
    private Long durationMs;
    private Instant createdAt;
}
```

`dto/AdminTraceDetailResponse.java`：

```java
package com.devknowledge.dto;

import lombok.Data;

import java.util.List;

/**
 * requestId 对应的完整请求链路（trace + spans）。
 */
@Data
public class AdminTraceDetailResponse {
    private AdminRequestTraceResponse trace;
    private List<AdminSpanResponse> spans;
}
```

`dto/FeedbackStatusRequest.java`：

```java
package com.devknowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 反馈状态变更请求。仅允许三个状态值。
 */
@Data
public class FeedbackStatusRequest {

    @NotBlank
    @Pattern(regexp = "NEW|IN_PROGRESS|RESOLVED", message = "状态仅允许 NEW / IN_PROGRESS / RESOLVED")
    private String status;
}
```

- [ ] **Step 3: AdminService 加方法**

`AdminService.java` 追加方法（全部照抄 boundedElastic 模式）：

```java
    public Mono<AdminErrorResponse> getError(String id) {
        return Mono.fromCallable(() -> adminMapper.findErrorById(id))
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<List<AdminErrorResponse>> listErrorsByRequestId(String requestId) {
        return Mono.fromCallable(() -> adminMapper.listErrorsByRequestId(requestId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<AdminTraceDetailResponse> getTraceDetail(String requestId) {
        return Mono.fromCallable(() -> {
                    AdminTraceDetailResponse detail = new AdminTraceDetailResponse();
                    detail.setTrace(adminMapper.findTraceByRequestId(requestId));
                    detail.setSpans(adminMapper.listSpansByRequestId(requestId));
                    return detail;
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<AdminPageResponse<AdminUserResponse>> listUsers(int page, int size) {
        return Mono.fromCallable(() -> {
                    int normalizedSize = normalizePageSize(size);
                    int requestedPage = Math.max(page, 1);
                    long total = adminMapper.countUsersForPage();
                    int totalPages = total == 0
                            ? 0
                            : (int) ((total + normalizedSize - 1) / normalizedSize);
                    int actualPage = totalPages == 0 ? 1 : Math.min(requestedPage, totalPages);
                    int offset = (actualPage - 1) * normalizedSize;
                    return new AdminPageResponse<>(
                            adminMapper.listUsers(offset, normalizedSize),
                            actualPage,
                            normalizedSize,
                            total,
                            totalPages,
                            totalPages > 0 && actualPage < totalPages,
                            actualPage > 1);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** status 传 null 或空 = 不筛选 */
    public Mono<AdminPageResponse<AdminFeedbackResponse>> listFeedbackPage(int page, int size, String status) {
        return Mono.fromCallable(() -> {
                    String normalizedStatus = (status == null || status.isBlank()) ? null : status;
                    int normalizedSize = normalizePageSize(size);
                    int requestedPage = Math.max(page, 1);
                    long total = adminMapper.countFeedbackByStatus(normalizedStatus);
                    int totalPages = total == 0
                            ? 0
                            : (int) ((total + normalizedSize - 1) / normalizedSize);
                    int actualPage = totalPages == 0 ? 1 : Math.min(requestedPage, totalPages);
                    int offset = (actualPage - 1) * normalizedSize;
                    return new AdminPageResponse<>(
                            adminMapper.listFeedbackPage(normalizedStatus, offset, normalizedSize),
                            actualPage,
                            normalizedSize,
                            total,
                            totalPages,
                            totalPages > 0 && actualPage < totalPages,
                            actualPage > 1);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** 反馈状态流转；返回更新行数（0 = 反馈不存在） */
    public Mono<Integer> updateFeedbackStatus(String id, String status) {
        return Mono.fromCallable(() -> adminMapper.updateFeedbackStatus(id, status))
                .subscribeOn(Schedulers.boundedElastic());
    }
```

同时**删除**旧的 `listFeedback(int limit)` 方法（被分页版取代）。

- [ ] **Step 4: AdminController 加端点**

`AdminController.java` 修改：

① `errors` 端点改为支持 requestId 参数（原方法替换）：

```java
    @GetMapping("/errors")
    public Mono<ResponseEntity<List<AdminErrorResponse>>> errors(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) String requestId) {
        if (!adminAccessService.isAdmin(authorization)) {
            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
        }
        if (requestId != null && !requestId.isBlank()) {
            return adminService.listErrorsByRequestId(requestId).map(ResponseEntity::ok);
        }
        return adminService.listErrors(limit).map(ResponseEntity::ok);
    }
```

② 追加新端点：

```java
    /** 单条错误详情（含完整堆栈） */
    @GetMapping("/errors/{id}")
    public Mono<ResponseEntity<AdminErrorResponse>> errorDetail(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String id) {
        if (!adminAccessService.isAdmin(authorization)) {
            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
        }
        return adminService.getError(id).map(ResponseEntity::ok);
    }

    /** requestId 对应的请求链路（trace + spans） */
    @GetMapping("/traces/detail")
    public Mono<ResponseEntity<AdminTraceDetailResponse>> traceDetail(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam String requestId) {
        if (!adminAccessService.isAdmin(authorization)) {
            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
        }
        return adminService.getTraceDetail(requestId).map(ResponseEntity::ok);
    }

    /** 用户列表（分页） */
    @GetMapping("/users")
    public Mono<ResponseEntity<AdminPageResponse<AdminUserResponse>>> users(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (!adminAccessService.isAdmin(authorization)) {
            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
        }
        return adminService.listUsers(page, size).map(ResponseEntity::ok);
    }

    /** 反馈分页 + 状态筛选 */
    @GetMapping("/feedback")
    public Mono<ResponseEntity<AdminPageResponse<AdminFeedbackResponse>>> feedback(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        if (!adminAccessService.isAdmin(authorization)) {
            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
        }
        return adminService.listFeedbackPage(page, size, status).map(ResponseEntity::ok);
    }

    /** 反馈状态流转 */
    @PatchMapping("/feedback/{id}/status")
    public Mono<ResponseEntity<Void>> updateFeedbackStatus(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String id,
            @jakarta.validation.Valid @RequestBody FeedbackStatusRequest request) {
        if (!adminAccessService.isAdmin(authorization)) {
            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
        }
        return adminService.updateFeedbackStatus(id, request.getStatus())
                .map(updated -> updated > 0
                        ? ResponseEntity.ok().<Void>build()
                        : ResponseEntity.status(HttpStatus.NOT_FOUND).<Void>build());
    }
```

③ import 区补 `org.springframework.web.bind.annotation.PatchMapping`、`jakarta.validation.Valid`、新 DTO。

- [ ] **Step 5: AdminService 单元测试**

`backend/src/test/java/com/devknowledge/service/AdminServiceTest.java`（新建；用真实对象无法直连 DB 的部分只测纯逻辑；状态校验靠 DTO `@Pattern`，Service 层测分页边界归一化——把 `normalizePageSize`/`normalizeLimit` 的逻辑通过新建受测实例验证不可行（private），改为测 FeedbackStatusRequest 正则）：

实际可行的纯逻辑测试放 `backend/src/test/java/com/devknowledge/dto/FeedbackStatusRequestTest.java`：

```java
package com.devknowledge.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("反馈状态请求校验")
class FeedbackStatusRequestTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private FeedbackStatusRequest request(String status) {
        FeedbackStatusRequest req = new FeedbackStatusRequest();
        req.setStatus(status);
        return req;
    }

    @Test
    @DisplayName("三个合法状态值通过校验")
    void acceptsValidStatuses() {
        for (String status : new String[]{"NEW", "IN_PROGRESS", "RESOLVED"}) {
            assertThat(validator.validate(request(status))).isEmpty();
        }
    }

    @Test
    @DisplayName("非法状态值被拒绝")
    void rejectsInvalidStatus() {
        assertThat(validator.validate(request("DELETED"))).isNotEmpty();
        assertThat(validator.validate(request("new"))).isNotEmpty();
        assertThat(validator.validate(request(""))).isNotEmpty();
        assertThat(validator.validate(null)).isNotEmpty();
    }
}
```

注意 `request(null)` 时 setter 收到 null，`@NotBlank` 会拦——把 `request(null)` 改为 `FeedbackStatusRequest req = new FeedbackStatusRequest(); req.setStatus(null); return req;` 的形式（即直接断言 null status 非空违规）。若 hibernate-validator 不在测试 classpath（Spring Boot web starter 自带，应有），先 `mvn test -q -Dtest=FeedbackStatusRequestTest` 验证。

- [ ] **Step 6: 编译 + 全量测试**

```bash
mvn test -q
```

Expected: BUILD SUCCESS。

---

### Task 4: 保留策略定时任务

**Files:**
- Modify: `backend/src/main/java/com/devknowledge/DevKnowledgeApplication.java`（@EnableScheduling）
- Create: `backend/src/main/java/com/devknowledge/service/AdminHousekeepingService.java`
- Modify: `backend/src/main/resources/application.yml`（observability 配置段）

**Interfaces:**
- Consumes: V21 的 `idx_request_spans_created_at` 索引。
- Produces: `AdminHousekeepingService.cleanupExpiredTraces()`（@Scheduled 入口，日志输出删除行数）。

- [ ] **Step 1: 主类启用调度**

`DevKnowledgeApplication.java`：

```java
package com.devknowledge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DevKnowledgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(DevKnowledgeApplication.class, args);
    }
}
```

- [ ] **Step 2: application.yml 加配置**

在 `app:` 段之前（或之后，保持 yml 分组清晰即可）加：

```yaml
observability:
  # request_traces / request_spans 保留天数，error_reports 与 user_feedback 永久保留
  trace-retention-days: 14
```

- [ ] **Step 3: 写 AdminHousekeepingService**

```java
package com.devknowledge.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 后台数据保留策略：定期清理过期的请求观测数据。
 * error_reports 与 user_feedback 有长期排查价值，永久保留，不参与清理。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminHousekeepingService {

    private final HousekeepingMapper housekeepingMapper;

    @Value("${observability.trace-retention-days:14}")
    private int retentionDays;

    /**
     * 每天凌晨 3:30 清理过期 traces 与 spans。
     * 删除失败只记日志，不影响服务，次日任务重试。
     */
    @Scheduled(cron = "0 30 3 * * *")
    public void cleanupExpiredTraces() {
        try {
            int traces = housekeepingMapper.deleteExpiredTraces(retentionDays);
            int spans = housekeepingMapper.deleteExpiredSpans(retentionDays);
            log.info("观测数据清理完成：traces 删除 {} 行，spans 删除 {} 行（保留 {} 天）",
                    traces, spans, retentionDays);
        } catch (Exception e) {
            log.warn("观测数据清理失败，将于次日重试: {}", e.getMessage());
        }
    }

    /** 清理用 Mapper（独立于 AdminMapper，职责单一） */
    @Mapper
    public interface HousekeepingMapper {

        @Select("SELECT COUNT(*) FROM request_traces WHERE created_at < NOW() - (${retentionDays} || ' days')::interval")
        long countExpiredTraces(@Param("retentionDays") int retentionDays);

        @Delete("DELETE FROM request_traces WHERE created_at < NOW() - (${retentionDays} || ' days')::interval")
        int deleteExpiredTraces(@Param("retentionDays") int retentionDays);

        @Delete("DELETE FROM request_spans WHERE created_at < NOW() - (${retentionDays} || ' days')::interval")
        int deleteExpiredSpans(@Param("retentionDays") int retentionDays);
    }
}
```

安全说明：`${retentionDays}` 是 `@Param` int，Java 侧已类型约束，非用户输入，无注入风险。

- [ ] **Step 4: 验证定时任务逻辑（用 countExpiredTraces 手工验证）**

```bash
mvn test -q
```

Expected: BUILD SUCCESS（context 能启动、@Scheduled bean 注册无异常——若现有测试不加载 Spring context，至少编译通过）。

启动后端验证一次（执行时做，不是提交项）：临时改 `trace-retention-days: 0` 启动，等调度或手动调用，确认 traces 被清；验证后改回 14。**注意：验证用真实库会清掉现有 420 条 traces——执行者应改为直接 SQL 验证删除条件**：

```bash
DB_URL=$(grep "url:" backend/src/main/resources/application.yml | head -1 | awk '{print $2}')
DB_NAME=$(echo "$DB_URL" | sed 's/.*\/\([a-zA-Z_]*\)$/\1/')
DB_USER=$(grep -A5 "datasource" backend/src/main/resources/application.yml | grep username | awk '{print $2}')
DB_PASS=$(grep -A5 "datasource" backend/src/main/resources/application.yml | grep password | awk '{print $2}')
PGPASSWORD=$DB_PASS psql -h localhost -U $DB_USER -d $DB_NAME \
  -c "SELECT COUNT(*) FROM request_traces WHERE created_at < NOW() - (14 || ' days')::interval;"
```

Expected: 返回 0（当前数据都小于 14 天，SQL 语义正确性靠这个表达式本身验证）。

- [ ] **Step 5: mvn test 全量回归**

```bash
mvn test -q
```

---

### Task 5: 前端 — API 客户端 + 错误上报堆栈

**Files:**
- Modify: `frontend/src/api/admin.ts`
- Modify: `frontend/src/utils/errorReporting.ts`
- Modify: `frontend/src/components/ClientErrorReporter.tsx`
- Modify: `frontend/src/api/client.ts`（4 处 reportClientError 加 errorDetail）

**Interfaces:**
- Consumes: Task 3 的后端契约（字段名逐一对应）。
- Produces: `adminApi.errorDetail(id)`、`adminApi.traceDetail(requestId)`、`adminApi.users(page,size)`、`adminApi.feedback(page,size,status)`、`adminApi.updateFeedbackStatus(id,status)`；`ClientErrorPayload.errorDetail?: string`。

- [ ] **Step 1: admin.ts 扩展**

`frontend/src/api/admin.ts` 修改：

① `AdminError` 接口加字段（`errorSummary` 后）：

```typescript
  errorDetail?: string
```

② 加类型（`AdminRequestTrace` 接口后）：

```typescript
export interface AdminSpan {
  stage: string
  status: string
  durationMs: number
  createdAt: string
}

export interface AdminTraceDetail {
  trace: AdminRequestTrace | null
  spans: AdminSpan[]
}

export interface AdminUser {
  id: string
  email: string
  displayName?: string
  createdAt: string
  lastActiveAt?: string
  totalTokens: number
  demoCount: number
  feedbackCount: number
}

export type FeedbackStatus = 'NEW' | 'IN_PROGRESS' | 'RESOLVED'
```

③ `adminApi` 对象整体替换为：

```typescript
export const adminApi = {
  overview: () => api.get<AdminOverview>('/admin/overview'),
  traces: (page = 1, size = 20) =>
    api.get<AdminPageResponse<AdminRequestTrace>>('/admin/traces', {
      page: String(page),
      size: String(size),
    }),
  errors: (limit = 50) => api.get<AdminError[]>('/admin/errors', { limit: String(limit) }),
  errorDetail: (id: string) => api.get<AdminError>(`/admin/errors/${id}`),
  traceDetail: (requestId: string) =>
    api.get<AdminTraceDetail>('/admin/traces/detail', { requestId }),
  users: (page = 1, size = 20) =>
    api.get<AdminPageResponse<AdminUser>>('/admin/users', {
      page: String(page),
      size: String(size),
    }),
  feedback: (page = 1, size = 20, status?: FeedbackStatus) =>
    api.get<AdminPageResponse<AdminFeedback>>('/admin/feedback', {
      page: String(page),
      size: String(size),
      ...(status ? { status } : {}),
    }),
  updateFeedbackStatus: (id: string, status: FeedbackStatus) =>
    api.patch(`/admin/feedback/${id}/status`, { status }),
}
```

④ 检查 `api/client.ts` 是否已有 `patch` 方法（`grep -n "patch" frontend/src/api/client.ts`）。若无需加（放 `Api` 类内、`get` 方法旁，照抄 get 的模式但 `method: 'PATCH'`，body JSON 序列化，无 SSE 逻辑）。以 client.ts 实际结构为准。

⑤ `AdminFeedback` 接口确认有 `status: string`（已有，不动）。

- [ ] **Step 2: errorReporting.ts 采集堆栈**

`frontend/src/utils/errorReporting.ts` 修改：

① `ClientErrorPayload` 接口加：

```typescript
  errorDetail?: string
```

② `reportClientError` 的 body 组装加（`errorSummary` 行后）：

```typescript
    errorDetail: payload.errorDetail
      ? payload.errorDetail
          .replace(/Bearer\s+[^\s]+/gi, 'Bearer [REDACTED]')
          .replace(/\b(api[-_ ]?key|password|secret)\s*[:=]\s*[^\s,;]+/gi, '$1=[REDACTED]')
          .slice(0, 16000)
      : undefined,
```

③ `sanitize` 保持不动（摘要用）。

- [ ] **Step 3: 上报触发点传堆栈**

`ClientErrorReporter.tsx` 的 `report` 函数签名改为带原始错误对象：

```typescript
    const report = (summary: string, errorType: string, error?: unknown) => {
```

（其余逻辑不变，`reportClientError` 调用加一行）：

```typescript
      reportClientError({
        errorSummary: summary || '未知前端错误',
        errorType,
        stage: 'frontend',
        errorDetail: error instanceof Error ? error.stack : undefined,
      })
```

两个 handler 传入原始错误：

```typescript
    const handleError = (event: ErrorEvent) => {
      report(
        event.error instanceof Error ? event.error.message : event.message,
        'UncaughtError',
        event.error,
      )
    }

    const handleRejection = (event: PromiseRejectionEvent) => {
      report(
        event.reason instanceof Error ? event.reason.message : 'UnhandledPromiseRejection',
        event.reason instanceof Error ? 'UnhandledRejection' : 'UnhandledRejection',
        event.reason,
      )
    }
```

`client.ts` 的 4 处 `reportClientError` 调用：`catch (error)` 分支里能拿到 Error 对象的（网络错误 2 处、SSE 2 处）加 `errorDetail: error instanceof Error ? error.stack : undefined`；HTTP 非 2xx 的 2 处没有 Error 对象，不加（保持现状）。

- [ ] **Step 4: 构建验证**

```bash
cd /Users/miaochencheng/IdeaProjects/DevKnowledge/frontend && npm run build
```

Expected: tsc + vite 通过。

---

### Task 6: 前端 — AdminPage 升级（错误抽屉 / 用户列表 / 反馈流转）

**Files:**
- Modify: `frontend/src/pages/AdminPage.tsx`
- Create: `frontend/src/components/admin/ErrorDetailDrawer.tsx`

**Interfaces:**
- Consumes: Task 5 的 `adminApi` 全部新方法与类型。
- Produces: 页面功能（无代码接口输出）。

- [ ] **Step 1: ErrorDetailDrawer 组件**

`frontend/src/components/admin/ErrorDetailDrawer.tsx`（完整文件）：

```tsx
import { useEffect, useState } from 'react'
import { adminApi, type AdminError, type AdminTraceDetail } from '@/api/admin'

/** 错误详情抽屉：完整堆栈 + 关联请求链路 + 环境信息 */
export function ErrorDetailDrawer({ error, onClose }: { error: AdminError; onClose: () => void }) {
  const [detail, setDetail] = useState<AdminError | null>(null)
  const [traceDetail, setTraceDetail] = useState<AdminTraceDetail | null>(null)

  useEffect(() => {
    // 详情以 id 精确拉取（列表项可能不含 errorDetail）
    adminApi.errorDetail(error.id).then(setDetail).catch(() => setDetail(error))
    if (error.requestId) {
      adminApi
        .traceDetail(error.requestId)
        .then(setTraceDetail)
        .catch(() => setTraceDetail(null))
    }
  }, [error])

  const merged = detail ?? error

  return (
    <div className="fixed inset-0 z-50 flex justify-end bg-black/40" onClick={onClose}>
      <div
        className="h-full w-full max-w-2xl overflow-y-auto bg-white p-6 dark:bg-gray-900"
        onClick={e => e.stopPropagation()}
      >
        <div className="mb-4 flex items-start justify-between">
          <div>
            <h2 className="text-lg font-bold text-gray-900 dark:text-gray-100">
              {merged.errorType || 'UnknownError'}
            </h2>
            <p className="mt-1 text-xs text-gray-500">
              {merged.source} · {merged.stage || '-'} · {new Date(merged.createdAt).toLocaleString('zh-CN')}
            </p>
          </div>
          <button
            onClick={onClose}
            className="rounded-md p-1 text-gray-400 hover:text-gray-600 dark:hover:text-gray-200"
          >
            ✕
          </button>
        </div>

        <p className="mb-6 rounded-lg bg-red-50 p-3 text-sm text-red-700 dark:bg-red-900/20 dark:text-red-400">
          {merged.errorSummary}
        </p>

        {/* 关联请求链路 */}
        <section className="mb-6">
          <h3 className="mb-2 text-sm font-medium text-gray-700 dark:text-gray-300">关联请求</h3>
          {!merged.requestId ? (
            <p className="text-xs text-gray-400">无关联请求</p>
          ) : !traceDetail || !traceDetail.trace ? (
            <p className="text-xs text-gray-400">无关联请求记录（trace 可能已被保留策略清理）</p>
          ) : (
            <div className="space-y-2">
              <div className="rounded-lg bg-gray-50 p-3 text-xs dark:bg-gray-800/70">
                <p>
                  {traceDetail.trace.method} {traceDetail.trace.path} · {traceDetail.trace.outcome}
                  {traceDetail.trace.statusCode ? ` (${traceDetail.trace.statusCode})` : ''} ·{' '}
                  {traceDetail.trace.totalMs} ms
                </p>
                {traceDetail.spans.length > 0 && (
                  <ul className="mt-2 space-y-1">
                    {traceDetail.spans.map((span, i) => (
                      <li key={i} className="flex items-center gap-2">
                        <span
                          className={`inline-block h-1.5 rounded-full bg-primary-500`}
                          style={{ width: `${Math.max(2, Math.min(100, span.durationMs / 10))}%` }}
                        />
                        <span>
                          {span.stage} · {span.status} · {span.durationMs} ms
                        </span>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            </div>
          )}
        </section>

        {/* 环境信息 */}
        <section className="mb-6">
          <h3 className="mb-2 text-sm font-medium text-gray-700 dark:text-gray-300">环境</h3>
          <dl className="grid grid-cols-2 gap-2 text-xs text-gray-600 dark:text-gray-400">
            <div><dt className="text-gray-400">页面</dt><dd>{merged.page || '-'}</dd></div>
            <div><dt className="text-gray-400">版本</dt><dd>{merged.appVersion || '-'}</dd></div>
            <div className="col-span-2"><dt className="text-gray-400">UserAgent</dt><dd className="break-all">{merged.userAgent || '-'}</dd></div>
            <div><dt className="text-gray-400">耗时</dt><dd>{merged.durationMs != null ? `${merged.durationMs} ms` : '-'}</dd></div>
            <div><dt className="text-gray-400">requestId</dt><dd className="break-all">{merged.requestId || '-'}</dd></div>
          </dl>
        </section>

        {/* 完整堆栈 */}
        <section>
          <h3 className="mb-2 text-sm font-medium text-gray-700 dark:text-gray-300">堆栈详情</h3>
          {merged.errorDetail ? (
            <pre className="max-h-96 overflow-auto rounded-lg bg-gray-950 p-4 text-xs leading-relaxed text-gray-200">
              {merged.errorDetail}
            </pre>
          ) : (
            <p className="text-xs text-gray-400">无堆栈信息</p>
          )}
        </section>
      </div>
    </div>
  )
}
```

- [ ] **Step 2: AdminPage 升级**

`frontend/src/pages/AdminPage.tsx` 修改（保持现有概览卡与 traces 区块不动）：

① import 区替换 admin 相关导入 + 新增：

```tsx
import {
  adminApi,
  type AdminError,
  type AdminFeedback,
  type AdminOverview,
  type AdminPageResponse,
  type AdminRequestTrace,
  type AdminUser,
  type FeedbackStatus,
} from '@/api/admin'
import { ErrorDetailDrawer } from '@/components/admin/ErrorDetailDrawer'
```

② 组件状态区加：

```tsx
  const [users, setUsers] = useState<AdminPageResponse<AdminUser> | null>(null)
  const [feedbackPage, setFeedbackPage] = useState<AdminPageResponse<AdminFeedback> | null>(null)
  const [feedbackStatus, setFeedbackStatus] = useState<FeedbackStatus | undefined>(undefined)
  const [feedbackPageNum, setFeedbackPageNum] = useState(1)
  const [selectedError, setSelectedError] = useState<AdminError | null>(null)
```

③ 初始加载改为同时拉用户列表与反馈第一页（`Promise.all` 数组加 `adminApi.users()`、`adminApi.feedback(1, 20)`；对应 setUsers / setFeedbackPage）。原 `setFeedback(feedItems)` 的 `feedback` state 删除。

④ 反馈区块整体替换为（含 tab / 分页 / 操作按钮 / 状态渲染）：

```tsx
      <section className="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-800 dark:bg-gray-900">
        <div className="mb-3 flex items-center justify-between">
          <h2 className="text-lg font-medium text-gray-900 dark:text-gray-100">用户反馈</h2>
          <div className="flex gap-1 text-sm">
            {([undefined, 'NEW', 'IN_PROGRESS', 'RESOLVED'] as const).map(tab => (
              <button
                key={tab ?? 'all'}
                onClick={() => { setFeedbackStatus(tab); setFeedbackPageNum(1) }}
                className={`rounded-md px-3 py-1 ${
                  feedbackStatus === tab
                    ? 'bg-primary-600 text-white'
                    : 'text-gray-500 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-800'
                }`}
              >
                {tab === undefined ? '全部' : feedbackStatusLabel(tab)}
              </button>
            ))}
          </div>
        </div>
        {/* 反馈列表 */}
        {(feedbackPage?.items ?? []).length === 0 ? (
          <p className="text-sm text-gray-500">暂无反馈记录</p>
        ) : (
          <div className="space-y-3">
            {(feedbackPage?.items ?? []).map(item => (
              <article key={item.id} className="rounded-lg bg-gray-50 p-3 text-sm dark:bg-gray-800/70">
                <div className="flex flex-wrap items-center gap-2 text-xs text-gray-500">
                  <span>{item.feedbackType}</span>
                  <span className={feedbackStatusClass(item.status)}>{feedbackStatusLabel(item.status)}</span>
                  <span>{formatDate(item.createdAt)}</span>
                </div>
                <p className="mt-1 whitespace-pre-wrap text-gray-800 dark:text-gray-200">{item.content}</p>
                {item.contact && <p className="mt-1 text-xs text-gray-500">联系方式：{item.contact}</p>}
                <div className="mt-2 flex gap-2">
                  {item.status !== 'IN_PROGRESS' && item.status !== 'RESOLVED' && (
                    <button onClick={() => handleFeedbackStatus(item.id, 'IN_PROGRESS')} className="...">标记处理中</button>
                  )}
                  {item.status !== 'RESOLVED' && (
                    <button onClick={() => handleFeedbackStatus(item.id, 'RESOLVED')} className="...">标记已解决</button>
                  )}
                  {item.status !== 'NEW' && (
                    <button onClick={() => handleFeedbackStatus(item.id, 'NEW')} className="...">重新打开</button>
                  )}
                </div>
              </article>
            ))}
          </div>
        )}
        {/* 反馈分页：复用 traces 分页按钮模式，调 loadFeedback(pageNum) */}
        ...
      </section>
```

（按钮 `className` 用现有次要按钮样式：`rounded-md border border-gray-300 px-2 py-1 text-xs text-gray-600 dark:border-gray-600 dark:text-gray-300`；分页复用 traces 区块的上一页/下一页结构，数据源 `feedbackPage`。）

⑤ 错误区块每条 `article` 加 `onClick={() => setSelectedError(item)}` 与 `cursor-pointer hover:bg-gray-100 dark:hover:bg-gray-800/40`；页面尾部渲染：

```tsx
      {selectedError && <ErrorDetailDrawer error={selectedError} onClose={() => setSelectedError(null)} />}
```

⑥ 用户区块（插在「最近请求」区块之前）：

```tsx
      <section className="rounded-xl border border-gray-200 bg-white p-4 dark:border-gray-800 dark:bg-gray-900">
        <h2 className="mb-3 text-lg font-medium text-gray-900 dark:text-gray-100">用户</h2>
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="text-xs text-gray-500">
              <tr>
                <th className="pb-2 pr-4">邮箱</th><th className="pb-2 pr-4">昵称</th>
                <th className="pb-2 pr-4">注册时间</th><th className="pb-2 pr-4">最近活跃</th>
                <th className="pb-2 pr-4">累计 Token</th><th className="pb-2 pr-4">Demo</th>
                <th className="pb-2">反馈</th>
              </tr>
            </thead>
            <tbody className="text-gray-800 dark:text-gray-200">
              {(users?.items ?? []).map(u => (
                <tr key={u.id} className="border-t border-gray-100 dark:border-gray-800">
                  <td className="py-2 pr-4">{u.email}</td>
                  <td className="py-2 pr-4">{u.displayName || '-'}</td>
                  <td className="py-2 pr-4 whitespace-nowrap">{formatDate(u.createdAt)}</td>
                  <td className="py-2 pr-4 whitespace-nowrap">{u.lastActiveAt ? formatDate(u.lastActiveAt) : '从未'}</td>
                  <td className="py-2 pr-4">{u.totalTokens}</td>
                  <td className="py-2 pr-4">{u.demoCount}</td>
                  <td className="py-2">{u.feedbackCount}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        {/* 用户分页同 traces 模式，数据源 users */}
      </section>
```

⑦ 辅助函数加在文件底部（formatDate 旁）：

```tsx
function feedbackStatusLabel(status: string) {
  return status === 'NEW' ? '新' : status === 'IN_PROGRESS' ? '处理中' : status === 'RESOLVED' ? '已解决' : status
}

function feedbackStatusClass(status: string) {
  if (status === 'NEW') return 'rounded-full bg-blue-100 px-2 py-0.5 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400'
  if (status === 'IN_PROGRESS') return 'rounded-full bg-amber-100 px-2 py-0.5 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400'
  return 'rounded-full bg-green-100 px-2 py-0.5 text-green-700 dark:bg-green-900/30 dark:text-green-400'
}
```

⑧ `handleFeedbackStatus` 实现（组件内）：

```tsx
  const handleFeedbackStatus = (id: string, status: FeedbackStatus) => {
    adminApi
      .updateFeedbackStatus(id, status)
      .then(() => {
        notify('状态已更新', 'success')
        return adminApi.feedback(feedbackPageNum, 20, feedbackStatus)
      })
      .then(setFeedbackPage)
      .catch(err => notify(err instanceof Error ? err.message : '状态更新失败', 'error'))
  }
```

（`loadFeedback` 同理：`adminApi.feedback(pageNum, 20, feedbackStatus).then(setFeedbackPage)`，tab 切换时也要触发加载——用 `useEffect` 监听 `[feedbackStatus, feedbackPageNum]`。）

- [ ] **Step 3: 构建验证**

```bash
cd /Users/miaochencheng/IdeaProjects/DevKnowledge/frontend && npm run build
```

Expected: tsc + vite 通过。

---

### Task 7: 集成验证 + 文档同步

**Files:**
- Modify: `api-docs.md`（新增/变更接口契约）
- Modify: `进度.md`
- Modify: `CODE_MAP.md`（如涉及新关键文件）

**Interfaces:**
- Consumes: Task 1-6 全部。
- Produces: 文档。

- [ ] **Step 1: 启动后端（Flyway V21 自动执行）**

```bash
cd /Users/miaochencheng/IdeaProjects/DevKnowledge/backend && mvn spring-boot:run
```

启动日志确认 `Successfully applied 1 migration to schema "public"`（V21）。

- [ ] **Step 2: 接口冒烟（curl）**

```bash
# 取 admin token（用白名单邮箱账号登录；若无密码，临时注册一个白名单邮箱账号）
TOKEN=...
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/admin/users | head -c 500
curl -s -H "Authorization: Bearer $TOKEN" "http://localhost:8080/api/admin/feedback?page=1&size=5" | head -c 500
# 错误详情（id 从 /admin/errors 拿）
curl -s -H "Authorization: Bearer $TOKEN" "http://localhost:8080/api/admin/errors" | head -c 300
```

Expected: users 返回 3 个用户（含 tour-test）；feedback 返回分页结构；错误详情结构正确。

- [ ] **Step 3: 前端手动走查**

启动 `npm run dev`，管理员账号登录 → /admin：

1. 用户区块显示 3 行，活跃时间/Token/Demo/反馈数正确；
2. 错误列表点击任一条 → 抽屉打开：堆栈区（存量数据无堆栈显示「无堆栈信息」）/ 环境 / 关联请求（存量 trace 在则显示）；
3. 反馈 tab 切换筛选正确；操作按钮改状态后列表即时刷新；非法操作（开发者工具直接 PATCH DELETED）被 400 拒绝；
4. 暗色模式正常。

- [ ] **Step 4: api-docs.md 更新**

在 admin 相关小节追加/更新：

```markdown
### 开发者后台（/api/admin，白名单鉴权）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | /api/admin/overview | 概览统计（不变） |
| GET | /api/admin/traces?page&size | 请求分页（不变） |
| GET | /api/admin/traces/detail?requestId= | 请求链路详情（trace + spans）【新】 |
| GET | /api/admin/errors?limit&requestId | 错误列表（新增 requestId 过滤）【变】 |
| GET | /api/admin/errors/{id} | 错误详情（含 errorDetail 完整堆栈）【新】 |
| GET | /api/admin/users?page&size | 用户列表（含活跃/Token/Demo/反馈聚合）【新】 |
| GET | /api/admin/feedback?page&size&status | 反馈分页 + 状态筛选（原数组→分页对象）【变】 |
| PATCH | /api/admin/feedback/{id}/status | 反馈状态流转，body {"status":"NEW|IN_PROGRESS|RESOLVED"}【新】 |
```

- [ ] **Step 5: 进度.md / CODE_MAP.md 更新**

`进度.md` 待办区追加：

```markdown
### 开发者后台升级 ✅

- [X] **traces 保留策略** — request_traces/request_spans 保留 14 天（`observability.trace-retention-days` 可配），每日 3:30 定时清理；error_reports/user_feedback 永久保留
- [X] **错误详情与链路追溯** — error_reports 新增 error_detail 存完整堆栈（前后端两条采集路径，脱敏入库）；后台错误可点开抽屉看堆栈/环境/关联请求链路
- [X] **用户列表** — /api/admin/users 分页接口 + 后台用户表格（活跃时间/累计 Token/Demo 数/反馈数）
- [X] **反馈管理** — 状态流转（NEW/IN_PROGRESS/RESOLVED）+ 状态筛选 tab + 分页
- [X] **前端错误上报增强** — ClientErrorReporter/client.ts 上报携带 error.stack
```

`CODE_MAP.md` 关键文件表 `GuidedTour` 行后加：

```markdown
| `backend/.../service/AdminHousekeepingService.java` | ★★☆ | 观测数据保留策略（traces 14 天定时清理） |
```

- [ ] **Step 6: 最终全量验证**

```bash
cd /Users/miaochencheng/IdeaProjects/DevKnowledge/backend && mvn test -q
cd /Users/miaochencheng/IdeaProjects/DevKnowledge/frontend && npm run build
```

Expected: 双通过。

---

## 自审记录（写计划时已核对）

1. **Spec 覆盖**：保留策略（Task 4）、错误详情+链路（Task 1/2/3/5/6）、用户列表（Task 3/5/6）、反馈流转（Task 3/5/6）、测试要求（各任务内嵌 + Task 7 集成）。spec 第 5 节变更清单逐项对应。
2. **占位符检查**：Task 6 Step 2 的反馈区块有 `...` 省略（分页部分标注"复用 traces 模式"并给出数据源）——这是对现有代码模式的引用而非实现缺失，AdminPage.tsx 现有分页代码就在同文件，执行者可直接看到；其余步骤代码完整。
3. **类型一致性**：`errorDetail`（DB `error_detail`）、`FeedbackStatus`（'NEW'|'IN_PROGRESS'|'RESOLVED'）、`AdminUser` 字段与 Task 3 Mapper SELECT 别名一一对应（lastActiveAt/totalTokens/demoCount/feedbackCount）。
4. **已知破坏性变更**：`GET /api/admin/feedback` 从数组改分页对象——唯一消费方是 AdminPage（同批修改），无其他调用方（已 grep 确认前端仅 admin.ts 消费）。
5. **Snapshot record 加字段**：唯一构造点在 `snapshot()` 内部（已核实），Task 2 Step 5 的修改自洽。
