package com.devknowledge.mapper;

import com.devknowledge.dto.AdminErrorResponse;
import com.devknowledge.dto.AdminFeedbackResponse;
import com.devknowledge.dto.AdminRequestTraceResponse;
import com.devknowledge.dto.AdminSpanResponse;
import com.devknowledge.dto.AdminUserResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 开发者后台聚合查询。
 * 查询只返回统计数据和已经脱敏的错误/反馈记录。
 */
@Mapper
public interface AdminMapper {

    @Select("SELECT COUNT(*) FROM users")
    long countUsers();

    /**
     * 汇总 Demo 生成估算 token 和 Embedding 消耗 token。
     */
    @Select("""
            SELECT COALESCE((SELECT SUM(COALESCE(tokens_used, 0)) FROM demos), 0)
                 + COALESCE((SELECT SUM(COALESCE(prompt_tokens, 0)) FROM embedding_usage), 0)
            """)
    long sumTokens();

    @Select("SELECT COUNT(*) FROM request_traces")
    long countRequests();

    @Select("SELECT COUNT(*) FROM request_traces WHERE outcome = 'SUCCESS'")
    long countSuccessfulRequests();

    @Select("SELECT COUNT(*) FROM request_traces")
    long countTraces();

    @Select("SELECT COALESCE(AVG(total_ms), 0) FROM request_traces")
    double averageLatencyMs();

    @Select("""
            SELECT COALESCE(
                percentile_cont(0.95) WITHIN GROUP (ORDER BY total_ms), 0
            ) FROM request_traces
            """)
    double p95LatencyMs();

    @Select("SELECT COUNT(*) FROM error_reports")
    long countErrors();

    @Select("SELECT COUNT(*) FROM user_feedback")
    long countFeedback();

    @Select("""
            SELECT request_id AS requestId, method, path,
                   status_code AS statusCode, outcome,
                   total_ms AS totalMs, first_event_ms AS firstEventMs,
                   first_text_ms AS firstTextMs, created_at AS createdAt
            FROM request_traces
            ORDER BY created_at DESC
            LIMIT #{size} OFFSET #{offset}
            """)
    List<AdminRequestTraceResponse> listTraces(
            @Param("offset") int offset,
            @Param("size") int size);

    @Select("""
            SELECT id, request_id AS requestId, user_id AS userId, source, stage,
                   error_type AS errorType, error_summary AS errorSummary,
                   method, path, page, app_version AS appVersion,
                   user_agent AS userAgent, environment,
                   duration_ms AS durationMs, created_at AS createdAt
            FROM error_reports
            ORDER BY created_at DESC
            LIMIT #{limit}
            """)
    List<AdminErrorResponse> listErrors(@Param("limit") int limit);

    /** 错误详情（含完整堆栈） */
    @Select("""
            SELECT id, request_id AS requestId, user_id AS userId, source, stage,
                   error_type AS errorType, error_summary AS errorSummary,
                   error_detail AS errorDetail,
                   method, path, page, app_version AS appVersion,
                   user_agent AS userAgent, environment,
                   duration_ms AS durationMs, created_at AS createdAt
            FROM error_reports
            WHERE id = #{id}::uuid
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

    /** requestId 对应的请求记录（链路追溯，取最新一条） */
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
}
