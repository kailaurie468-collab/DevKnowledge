-- V8__rag_vector_search.sql

-- 1. 启用 pgvector 扩展
CREATE EXTENSION IF NOT EXISTS vector;

-- 2. 用户 Embedding 配置表
CREATE TABLE user_embedding_configs (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    name VARCHAR(100),
    api_key TEXT NOT NULL,
    base_url VARCHAR(500) DEFAULT 'https://api.openai.com/v1',
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_user_embedding_configs_active
    ON user_embedding_configs(user_id) WHERE is_active = true;

-- 3. Embedding Token 消耗表
CREATE TABLE embedding_usage (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    config_id UUID NOT NULL REFERENCES user_embedding_configs(id),
    prompt_tokens INT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_embedding_usage_user_date ON embedding_usage(user_id, created_at);

-- 4. 文档切片表（向量存储）
CREATE TABLE kb_chunks (
    id UUID PRIMARY KEY,
    kb_id UUID NOT NULL REFERENCES knowledge_bases(id) ON DELETE CASCADE,
    doc_id UUID NOT NULL REFERENCES kb_documents(id) ON DELETE CASCADE,
    chunk_index INT NOT NULL,
    content TEXT NOT NULL,
    embedding vector(1536),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_kb_chunks_kb_id ON kb_chunks(kb_id);
CREATE INDEX idx_kb_chunks_doc_id ON kb_chunks(doc_id);
CREATE INDEX idx_kb_chunks_embedding ON kb_chunks
    USING hnsw (embedding vector_cosine_ops);

-- 5. kb_documents 新增 chunk_count 字段
ALTER TABLE kb_documents ADD COLUMN chunk_count INT DEFAULT 0;

-- 6. knowledge_bases 新增 embedding_model 和 embedding_dimensions 字段
ALTER TABLE knowledge_bases ADD COLUMN embedding_model VARCHAR(100) NOT NULL DEFAULT 'text-embedding-3-small';
ALTER TABLE knowledge_bases ADD COLUMN embedding_dimensions INT;
