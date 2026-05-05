-- 多 AI 配置支持：移除唯一约束，新增 name 和 is_active 字段
ALTER TABLE user_ai_configs DROP CONSTRAINT IF EXISTS user_ai_configs_user_id_key;
ALTER TABLE user_ai_configs ADD COLUMN name VARCHAR(100) NOT NULL DEFAULT '';
ALTER TABLE user_ai_configs ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT false;

-- 已有数据迁移：设置默认名称和激活状态
UPDATE user_ai_configs SET name = provider, is_active = true WHERE name = '';

-- 部分唯一索引：每个用户最多一个激活配置
CREATE UNIQUE INDEX idx_user_ai_configs_active ON user_ai_configs (user_id) WHERE is_active = true;
