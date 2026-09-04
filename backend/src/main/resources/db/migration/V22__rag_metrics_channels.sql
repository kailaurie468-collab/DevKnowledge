-- V22: RAG 指标细化——检索通道明细
-- avg_similarity 语义修正：改为向量通道余弦相似度（历史数据为 RRF 排名分，不可比，前端按 vector_count 是否为空区分口径）
-- bm25_count / vector_count：两通道各自召回条数
-- merged_count：RRF 融合（或截断）后最终返回条数
-- rerank_used：本次检索是否经过 Reranker 精排

ALTER TABLE rag_metrics ADD COLUMN bm25_count INT;
ALTER TABLE rag_metrics ADD COLUMN vector_count INT;
ALTER TABLE rag_metrics ADD COLUMN merged_count INT;
ALTER TABLE rag_metrics ADD COLUMN rerank_used BOOLEAN NOT NULL DEFAULT false;
