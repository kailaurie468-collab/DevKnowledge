-- V21: 开发者后台升级
-- error_reports 新增完整堆栈列；request_spans 补 created_at 索引（保留策略清理用）

ALTER TABLE error_reports ADD COLUMN error_detail TEXT;

CREATE INDEX idx_request_spans_created_at ON request_spans(created_at DESC);
