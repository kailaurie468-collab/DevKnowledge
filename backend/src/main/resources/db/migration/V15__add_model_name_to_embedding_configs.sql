-- V15: 给 user_embedding_configs 添加 model_name 字段
-- Embedding 模型名现在存储在配置中，而不是知识库中

ALTER TABLE user_embedding_configs ADD COLUMN model_name VARCHAR(100) DEFAULT 'text-embedding-3-small';
