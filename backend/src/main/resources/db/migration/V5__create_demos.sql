-- Demo 生成记录表
CREATE TABLE demos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,  -- 允许匿名生成
    title VARCHAR(300) NOT NULL,                           -- Demo 标题（AI 生成）
    prompt TEXT NOT NULL,                                  -- 用户输入的 prompt
    framework_id UUID REFERENCES frameworks(id),           -- 关联框架（可选）
    code_content TEXT NOT NULL,                            -- 生成的代码
    explanation TEXT NOT NULL,                             -- 代码解释
    language VARCHAR(50) NOT NULL,                         -- 编程语言
    tags TEXT[] NOT NULL DEFAULT '{}',                     -- 标签
    tokens_used INTEGER,                                   -- 消耗的 token 数
    model_version VARCHAR(100),                            -- 使用的模型版本
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_demos_user_id ON demos (user_id);
CREATE INDEX idx_demos_created_at ON demos (created_at DESC);
