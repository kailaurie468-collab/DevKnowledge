package com.devknowledge.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * 单个请求的链路计时上下文。
 * 使用单调递增的 System.nanoTime()，避免系统时间校准影响耗时结果。
 */
public final class RequestTiming {

    /** Reactor Context 中保存计时上下文的 key */
    public static final String CONTEXT_KEY = RequestTiming.class.getName();

    private final String requestId;
    private final String method;
    private final String path;
    private final String userAgent;
    private final String clientVersion;
    private final long startedAtNanos = System.nanoTime();
    private final AtomicLong firstEventAtNanos = new AtomicLong(-1);
    private final AtomicLong firstTextAtNanos = new AtomicLong(-1);
    private final List<StageSnapshot> spans = Collections.synchronizedList(new ArrayList<>());

    private volatile String logicalErrorCode;
    private volatile String logicalErrorMessage;
    private volatile String logicalErrorStackTrace;

    public RequestTiming(
            String requestId,
            String method,
            String path,
            String userAgent,
            String clientVersion) {
        this.requestId = requestId;
        this.method = method;
        this.path = path;
        this.userAgent = userAgent;
        this.clientVersion = clientVersion;
    }

    /**
     * 只记录首次 SSE 事件，后续事件不覆盖首事件时间。
     */
    public void markFirstEvent() {
        firstEventAtNanos.compareAndSet(-1, System.nanoTime());
    }

    /**
     * 只记录首次文本事件，用于衡量用户真正看到内容的等待时间。
     */
    public void markFirstText() {
        firstTextAtNanos.compareAndSet(-1, System.nanoTime());
    }

    /**
     * 记录业务层已转换为 SSE error 事件的逻辑错误。
     * 这类错误会正常 complete，WebFilter 只能通过这里识别为 ERROR。
     */
    public void markLogicalError(String errorCode, String errorMessage) {
        if (logicalErrorCode == null) {
            logicalErrorCode = errorCode;
            logicalErrorMessage = errorMessage;
        }
    }

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

    /**
     * 开始一个关键业务阶段，例如 RAG、BM25、向量检索或 LLM 生成。
     */
    public Stage startStage(String stage) {
        return new Stage(stage, System.nanoTime());
    }

    /**
     * 执行并记录一个同步业务阶段，适合包装 MyBatis 和同步外部调用。
     */
    public <T> T measureStage(String stage, Supplier<T> operation) {
        Stage timingStage = startStage(stage);
        try {
            T result = operation.get();
            timingStage.finish("SUCCESS");
            return result;
        } catch (RuntimeException | Error error) {
            timingStage.finish("ERROR");
            throw error;
        }
    }

    /**
     * 生成请求最终快照。快照生成本身不执行 IO，交由异步记录器保存。
     */
    public Snapshot snapshot(
            String requestedOutcome,
            Integer statusCode,
            Throwable error,
            UUID userId) {
        String errorCode = logicalErrorCode;
        String errorMessage = logicalErrorMessage;
        String errorStackTrace = logicalErrorStackTrace;
        if (error != null) {
            errorCode = error.getClass().getSimpleName();
            errorMessage = error.getMessage();
            errorStackTrace = renderStackTrace(error);
        }

        String outcome = requestedOutcome;
        if (error instanceof TimeoutException) {
            outcome = "TIMEOUT";
        } else if (error != null || errorCode != null) {
            outcome = "ERROR";
        }

        return new Snapshot(
                requestId,
                userId,
                method,
                path,
                outcome,
                statusCode,
                elapsedMillis(startedAtNanos),
                elapsedSinceStart(firstEventAtNanos.get()),
                elapsedSinceStart(firstTextAtNanos.get()),
                errorCode,
                errorMessage,
                errorStackTrace,
                userAgent,
                clientVersion,
                List.copyOf(spans),
                Instant.now());
    }

    private Long elapsedSinceStart(long eventAtNanos) {
        return eventAtNanos < 0 ? null : elapsedMillis(startedAtNanos, eventAtNanos);
    }

    private static long elapsedMillis(long startNanos) {
        return elapsedMillis(startNanos, System.nanoTime());
    }

    private static long elapsedMillis(long startNanos, long endNanos) {
        return Math.max(0, (endNanos - startNanos) / 1_000_000);
    }

    /**
     * 一个阶段只能结束一次，避免异常回调和取消回调重复写入 Span。
     */
    public final class Stage {
        private final String stage;
        private final long startedAtNanos;
        private final AtomicBoolean finished = new AtomicBoolean();

        private Stage(String stage, long startedAtNanos) {
            this.stage = stage;
            this.startedAtNanos = startedAtNanos;
        }

        public void finish(String status) {
            if (finished.compareAndSet(false, true)) {
                spans.add(new StageSnapshot(
                        stage,
                        status,
                        elapsedMillis(startedAtNanos),
                        Instant.now()));
            }
        }
    }

    public record StageSnapshot(
            String stage,
            String status,
            long durationMs,
            Instant createdAt) {
    }

    public record Snapshot(
            String requestId,
            UUID userId,
            String method,
            String path,
            String outcome,
            Integer statusCode,
            long totalMs,
            Long firstEventMs,
            Long firstTextMs,
            String errorCode,
            String errorMessage,
            String errorStackTrace,
            String userAgent,
            String clientVersion,
            List<StageSnapshot> spans,
            Instant createdAt) {
    }
}
