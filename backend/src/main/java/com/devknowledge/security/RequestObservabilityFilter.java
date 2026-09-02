package com.devknowledge.security;

import com.devknowledge.service.RequestObservabilityService;
import com.devknowledge.service.RequestTiming;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

import java.util.UUID;

/**
 * HTTP 请求级观测入口。
 * 只记录请求元数据和脱敏摘要，真正的 MyBatis 写入由观测服务异步完成。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class RequestObservabilityFilter implements WebFilter {

    public static final String REQUEST_ID_ATTRIBUTE = "requestId";
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String CLIENT_VERSION_HEADER = "X-Client-Version";

    private final RequestObservabilityService observabilityService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String requestId = resolveRequestId(request.getHeaders().getFirst(REQUEST_ID_HEADER));
        RequestTiming timing = new RequestTiming(
                requestId,
                request.getMethod() != null ? request.getMethod().name() : "UNKNOWN",
                request.getPath().value(),
                request.getHeaders().getFirst("User-Agent"),
                request.getHeaders().getFirst(CLIENT_VERSION_HEADER));

        exchange.getAttributes().put(REQUEST_ID_ATTRIBUTE, requestId);
        exchange.getAttributes().put(RequestTiming.CONTEXT_KEY, timing);
        exchange.getResponse().getHeaders().set("X-Request-Id", requestId);

        return chain.filter(exchange)
                .doOnError(timing::markError)
                .doFinally(signalType -> {
                    HttpStatusCode status = exchange.getResponse().getStatusCode();
                    int statusCode = status != null ? status.value() : 200;
                    UUID userId = parseUserId(exchange.getAttribute("userId"));
                    String outcome = resolveOutcome(signalType, statusCode);
                    observabilityService.recordTrace(
                            timing.snapshot(outcome, statusCode, null, userId));
                })
                .contextWrite(context -> context.put(RequestTiming.CONTEXT_KEY, timing));
    }

    private String resolveOutcome(SignalType signalType, int statusCode) {
        if (signalType == SignalType.CANCEL) {
            return "CANCELLED";
        }
        if (statusCode >= 400) {
            return "ERROR";
        }
        return "SUCCESS";
    }

    private UUID parseUserId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /**
     * 复用前端传来的合法 UUID，非法或缺失时由服务端生成，避免把任意文本写入日志和数据库。
     */
    private String resolveRequestId(String requestedId) {
        if (requestedId != null) {
            try {
                return UUID.fromString(requestedId).toString();
            } catch (IllegalArgumentException ignored) {
                // 忽略非法客户端 ID，继续生成服务端 ID
            }
        }
        return UUID.randomUUID().toString();
    }
}
