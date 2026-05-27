-- V9__rag_metrics.sql
-- RAG 检索指标表：记录每次 Demo 生成时的 RAG 检索质量数据

CREATE TABLE rag_metrics (
    id              UUID PRIMARY KEY,
    demo_id         UUID UNIQUE REFERENCES demos(id) ON DELETE CASCADE,
    user_id         UUID NOT NULL REFERENCES users(id),
    kb_id           UUID NOT NULL,
    rag_used        BOOLEAN NOT NULL DEFAULT false,
    top_k           INT,
    chunk_count     INT,
    avg_similarity  DOUBLE PRECISION,
    max_similarity  DOUBLE PRECISION,
    min_similarity  DOUBLE PRECISION,
    retrieval_ms    INT,
    tool_call_count INT DEFAULT 0,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_rag_metrics_user_created ON rag_metrics(user_id, created_at DESC);
