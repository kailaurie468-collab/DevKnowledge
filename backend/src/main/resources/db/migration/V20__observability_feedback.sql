-- V20: 请求可观测性、错误上报和用户反馈
-- 仅保存脱敏上下文与耗时，不保存 Prompt、API Key、密码或完整 AI 输出

CREATE TABLE request_traces (
    id              UUID PRIMARY KEY,
    request_id      VARCHAR(64) UNIQUE NOT NULL,
    user_id         UUID REFERENCES users(id) ON DELETE SET NULL,
    method          VARCHAR(16) NOT NULL,
    path            VARCHAR(512) NOT NULL,
    status_code     INT,
    outcome         VARCHAR(16) NOT NULL,
    total_ms        BIGINT NOT NULL DEFAULT 0,
    first_event_ms  BIGINT,
    first_text_ms   BIGINT,
    error_code      VARCHAR(128),
    error_summary   VARCHAR(2000),
    user_agent      VARCHAR(512),
    client_version  VARCHAR(128),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_request_traces_created_at ON request_traces(created_at DESC);
CREATE INDEX idx_request_traces_path_created ON request_traces(path, created_at DESC);
CREATE INDEX idx_request_traces_outcome_created ON request_traces(outcome, created_at DESC);

CREATE TABLE request_spans (
    id              UUID PRIMARY KEY,
    request_id      VARCHAR(64) NOT NULL,
    stage           VARCHAR(64) NOT NULL,
    status          VARCHAR(16) NOT NULL,
    duration_ms     BIGINT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_request_spans_request_id ON request_spans(request_id);
CREATE INDEX idx_request_spans_stage_created ON request_spans(stage, created_at DESC);

CREATE TABLE error_reports (
    id              UUID PRIMARY KEY,
    request_id      VARCHAR(64),
    user_id         UUID REFERENCES users(id) ON DELETE SET NULL,
    source          VARCHAR(16) NOT NULL,
    stage           VARCHAR(64),
    error_type      VARCHAR(128),
    error_summary   VARCHAR(2000) NOT NULL,
    method          VARCHAR(16),
    path            VARCHAR(512),
    page            VARCHAR(512),
    app_version     VARCHAR(128),
    user_agent      VARCHAR(512),
    environment     VARCHAR(64),
    duration_ms     BIGINT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_error_reports_created_at ON error_reports(created_at DESC);
CREATE INDEX idx_error_reports_source_created ON error_reports(source, created_at DESC);

CREATE TABLE user_feedback (
    id              UUID PRIMARY KEY,
    request_id      VARCHAR(64),
    user_id         UUID REFERENCES users(id) ON DELETE SET NULL,
    feedback_type   VARCHAR(64) NOT NULL,
    content         TEXT NOT NULL,
    contact         VARCHAR(255),
    page            VARCHAR(512),
    status          VARCHAR(16) NOT NULL DEFAULT 'NEW',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_user_feedback_created_at ON user_feedback(created_at DESC);
CREATE INDEX idx_user_feedback_status_created ON user_feedback(status, created_at DESC);
