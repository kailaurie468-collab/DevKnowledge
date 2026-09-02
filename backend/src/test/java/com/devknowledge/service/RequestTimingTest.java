package com.devknowledge.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RequestTiming 请求链路计时")
class RequestTimingTest {

    @Test
    @DisplayName("只记录首个 SSE 事件和首个文本事件")
    void recordsFirstEventsOnly() {
        RequestTiming timing = new RequestTiming(
                "request-1", "POST", "/api/demos/generate", "test-agent", "test");

        timing.markFirstEvent();
        timing.markFirstEvent();
        timing.markFirstText();
        timing.markFirstText();

        RequestTiming.Snapshot snapshot =
                timing.snapshot("SUCCESS", 200, null, null);

        assertThat(snapshot.requestId()).isEqualTo("request-1");
        assertThat(snapshot.firstEventMs()).isNotNull();
        assertThat(snapshot.firstTextMs()).isNotNull();
        assertThat(snapshot.firstTextMs()).isGreaterThanOrEqualTo(snapshot.firstEventMs());
    }

    @Test
    @DisplayName("阶段计时保存阶段名称、耗时和状态")
    void recordsStageTiming() {
        RequestTiming timing = new RequestTiming(
                "request-2", "GET", "/api/test", null, null);

        RequestTiming.Stage stage = timing.startStage("vector");
        stage.finish("SUCCESS");

        RequestTiming.Snapshot snapshot =
                timing.snapshot("SUCCESS", 200, null, null);

        assertThat(snapshot.spans())
                .singleElement()
                .satisfies(span -> {
                    assertThat(span.stage()).isEqualTo("vector");
                    assertThat(span.status()).isEqualTo("SUCCESS");
                    assertThat(span.durationMs()).isGreaterThanOrEqualTo(0);
                });
    }

    @Test
    @DisplayName("超时异常被识别为 TIMEOUT")
    void identifiesTimeout() {
        RequestTiming timing = new RequestTiming(
                "request-3", "POST", "/api/test", null, null);

        RequestTiming.Snapshot snapshot =
                timing.snapshot("ERROR", 500, new TimeoutException("timeout"), null);

        assertThat(snapshot.outcome()).isEqualTo("TIMEOUT");
        assertThat(snapshot.errorCode()).isEqualTo("TimeoutException");
    }
}
