-- 用户 AI 服务商配置表
-- 每个用户只能有一条配置，API Key 使用 AES 加密存储
CREATE TABLE user_ai_configs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    provider VARCHAR(50) NOT NULL,                        -- openai / anthropic / deepseek / custom
    api_key VARCHAR(500) NOT NULL,                        -- AES 加密后的密文
    base_url VARCHAR(500) NOT NULL,                       -- API 基础地址
    model VARCHAR(100) NOT NULL,                          -- 模型名称
    max_tokens INTEGER NOT NULL DEFAULT 4096,             -- 单次最大输出 token 数
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_user_ai_configs_user_id ON user_ai_configs (user_id);
