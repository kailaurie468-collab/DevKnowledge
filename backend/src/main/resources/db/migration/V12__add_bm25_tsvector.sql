-- V12: BM25 全文检索支持
-- 为 kb_chunks 新增 tsvector 列，存储 Jieba 分词后的词项，配合 GIN 索引实现快速关键词检索

-- 添加 tsvector 列（允许 NULL，存量数据不回填，新入库数据自动填充）
ALTER TABLE kb_chunks ADD COLUMN tsv tsvector;

-- GIN 索引加速 tsvector 的 @@ 匹配查询
CREATE INDEX idx_kb_chunks_tsv ON kb_chunks USING GIN(tsv);
