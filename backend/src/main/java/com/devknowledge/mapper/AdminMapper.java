package com.devknowledge.mapper;

import com.devknowledge.dto.AdminErrorResponse;
import com.devknowledge.dto.AdminFeedbackResponse;
import com.devknowledge.dto.AdminRequestTraceResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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

    @Select("""
            SELECT id, request_id AS requestId, user_id AS userId,
                   feedback_type AS feedbackType, content, contact, page,
                   status, created_at AS createdAt
            FROM user_feedback
            ORDER BY created_at DESC
            LIMIT #{limit}
            """)
    List<AdminFeedbackResponse> listFeedback(@Param("limit") int limit);
}
