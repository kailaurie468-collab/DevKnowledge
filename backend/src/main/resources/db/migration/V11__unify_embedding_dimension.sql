-- V11__unify_embedding_dimension.sql
-- 统一 Embedding 维度为 1024，去掉维度可选配置

-- 1. 删除旧的 HNSW 索引
DROP INDEX IF EXISTS idx_kb_chunks_embedding;

-- 2. 清除所有已有向量（旧维度 1536 与新维度 1024 不兼容）
UPDATE kb_chunks SET embedding = NULL;

-- 3. 将 embedding 列类型改为 vector(1024)
ALTER TABLE kb_chunks ALTER COLUMN embedding TYPE vector(1024);

-- 4. 重建 HNSW 索引
CREATE INDEX idx_kb_chunks_embedding ON kb_chunks
    USING hnsw (embedding vector_cosine_ops);

-- 5. 将所有 'ready' 文档标记为 'pending'，以便重新向量化
UPDATE kb_documents SET status = 'pending' WHERE status = 'ready';

-- 6. 删除 knowledge_bases 表的 embedding_dimensions 列
ALTER TABLE knowledge_bases DROP COLUMN IF EXISTS embedding_dimensions;
