package com.devknowledge.service;

import com.devknowledge.mapper.ErrorReportMapper;
import com.devknowledge.mapper.RequestSpanMapper;
import com.devknowledge.mapper.RequestTraceMapper;
import com.devknowledge.mapper.UserFeedbackMapper;
import com.devknowledge.model.ErrorReport;
import com.devknowledge.model.RequestSpan;
import com.devknowledge.model.RequestTrace;
import com.devknowledge.model.UserFeedback;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.UUID;

/**
 * 请求观测、错误上报和用户反馈记录服务。
 * MyBatis 是阻塞式 ORM，所有数据库写入都放到 boundedElastic 线程执行。
 */
@Service
@RequiredArgsConstructor
public class RequestObservabilityService {

    private static final Logger log = LoggerFactory.getLogger(RequestObservabilityService.class);

    private final RequestTraceMapper requestTraceMapper;
    private final RequestSpanMapper requestSpanMapper;
    private final ErrorReportMapper errorReportMapper;
    private final UserFeedbackMapper userFeedbackMapper;
    private final NotificationService notificationService;

    /**
     * 异步保存请求快照，不能让观测写库阻塞用户响应。
     */
    public void recordTrace(RequestTiming.Snapshot snapshot) {
        Mono.fromRunnable(() -> {
                    RequestTrace trace = new RequestTrace();
                    trace.setId(UUID.randomUUID());
                    trace.setRequestId(snapshot.requestId());
                    trace.setUserId(snapshot.userId());
                    trace.setMethod(snapshot.method());
                    trace.setPath(snapshot.path());
                    trace.setStatusCode(snapshot.statusCode());
                    trace.setOutcome(snapshot.outcome());
                    trace.setTotalMs(snapshot.totalMs());
                    trace.setFirstEventMs(snapshot.firstEventMs());
                    trace.setFirstTextMs(snapshot.firstTextMs());
                    trace.setErrorCode(snapshot.errorCode());
                    trace.setErrorSummary(snapshot.errorCode() == null
                            ? null
                            : SensitiveDataSanitizer.sanitize(snapshot.errorMessage()));
                    trace.setUserAgent(snapshot.userAgent());
                    trace.setClientVersion(snapshot.clientVersion());
                    trace.setCreatedAt(snapshot.createdAt());
                    requestTraceMapper.insert(trace);

                    for (RequestTiming.StageSnapshot stage : snapshot.spans()) {
                        RequestSpan span = new RequestSpan();
                        span.setId(UUID.randomUUID());
                        span.setRequestId(snapshot.requestId());
                        span.setStage(stage.stage());
                        span.setStatus(stage.status());
                        span.setDurationMs(stage.durationMs());
                        span.setCreatedAt(stage.createdAt());
                        requestSpanMapper.insert(span);
                    }

                    // 4xx 鉴权/参数错误通常是预期业务结果，不自动发邮件；只上报有明确异常上下文的错误
                    if (snapshot.errorCode() != null
                            && ("ERROR".equals(snapshot.outcome()) || "TIMEOUT".equals(snapshot.outcome()))) {
                        ErrorReport report = new ErrorReport();
                        report.setId(UUID.randomUUID());
                        report.setRequestId(snapshot.requestId());
                        report.setUserId(snapshot.userId());
                        report.setSource("BACKEND");
                        report.setStage(lastStage(snapshot));
                        report.setErrorType(snapshot.errorCode());
                        report.setErrorSummary(SensitiveDataSanitizer.sanitize(snapshot.errorMessage()));
                        report.setMethod(snapshot.method());
                        report.setPath(snapshot.path());
                        report.setAppVersion(snapshot.clientVersion());
                        report.setUserAgent(snapshot.userAgent());
                        report.setEnvironment("server");
                        report.setDurationMs(snapshot.totalMs());
                        report.setCreatedAt(snapshot.createdAt());
                        errorReportMapper.insert(report);
                        notificationService.sendAsync(
                                "DevKnowledge 错误上报 [BACKEND]",
                                buildErrorMail(report));
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        unused -> {
                        },
                        error -> log.warn("保存请求观测失败: requestId={}, reason={}",
                                snapshot.requestId(), error.getMessage()));
    }

    /**
     * 保存错误记录并异步通知开发者。邮件失败不会影响接口响应。
     */
    public Mono<Void> reportError(ErrorReport report) {
        report.setErrorSummary(SensitiveDataSanitizer.sanitize(report.getErrorSummary()));
        return Mono.fromRunnable(() -> errorReportMapper.insert(report))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(unused -> notificationService.sendAsync(
                        "DevKnowledge 错误上报 [" + report.getSource() + "]",
                        buildErrorMail(report)))
                .then();
    }

    private String lastStage(RequestTiming.Snapshot snapshot) {
        if (snapshot.spans().isEmpty()) {
            return null;
        }
        return snapshot.spans().get(snapshot.spans().size() - 1).stage();
    }

    /**
     * 保存用户反馈并异步发送到开发者邮箱。
     */
    public Mono<Void> submitFeedback(UserFeedback feedback) {
        feedback.setContent(SensitiveDataSanitizer.sanitize(feedback.getContent()));
        return Mono.fromRunnable(() -> userFeedbackMapper.insert(feedback))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(unused -> notificationService.sendAsync(
                        "DevKnowledge 用户反馈 [" + feedback.getFeedbackType() + "]",
                        buildFeedbackMail(feedback)))
                .then();
    }

    private String buildErrorMail(ErrorReport report) {
        return """
                requestId: %s
                source: %s
                stage: %s
                errorType: %s
                errorSummary: %s
                method: %s
                path: %s
                page: %s
                appVersion: %s
                environment: %s
                durationMs: %s
                """.formatted(
                safe(report.getRequestId()),
                safe(report.getSource()),
                safe(report.getStage()),
                safe(report.getErrorType()),
                safe(report.getErrorSummary()),
                safe(report.getMethod()),
                safe(report.getPath()),
                safe(report.getPage()),
                safe(report.getAppVersion()),
                safe(report.getEnvironment()),
                report.getDurationMs());
    }

    private String buildFeedbackMail(UserFeedback feedback) {
        return """
                requestId: %s
                feedbackType: %s
                content: %s
                contact: %s
                page: %s
                """.formatted(
                safe(feedback.getRequestId()),
                safe(feedback.getFeedbackType()),
                safe(feedback.getContent()),
                safe(feedback.getContact()),
                safe(feedback.getPage()));
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
