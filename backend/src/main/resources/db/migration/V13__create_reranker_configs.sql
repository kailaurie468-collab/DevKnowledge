-- V13: Qwen3-Reranker 精排配置表
-- 复用 EmbeddingConfig 模式，存储用户的 Reranker API 凭据

CREATE TABLE user_reranker_configs (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    name VARCHAR(100),
    api_key TEXT NOT NULL,
    base_url VARCHAR(500) DEFAULT 'https://api.siliconflow.cn/v1',
    model VARCHAR(100) DEFAULT 'Qwen/Qwen3-Reranker-0.6B',
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- 每个用户同时只能有一个激活的 Reranker 配置
CREATE UNIQUE INDEX idx_user_reranker_configs_active
    ON user_reranker_configs(user_id) WHERE is_active = true;
