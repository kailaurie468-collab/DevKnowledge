-- V14: Skills 模块核心表
-- skills / skill_steps / skill_suggestions / user_activities 四张表 + 索引

-- Skills 主表：存储用户提取或手动创建的 Skill
CREATE TABLE skills (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name                VARCHAR(200) NOT NULL,
    description         TEXT,
    category            VARCHAR(50),
    framework_id        UUID REFERENCES frameworks(id),
    trigger_description TEXT,
    exported_content    TEXT,
    version             INTEGER NOT NULL DEFAULT 1,
    is_public           BOOLEAN NOT NULL DEFAULT false,
    is_deleted          BOOLEAN NOT NULL DEFAULT false,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 索引：按用户查询 + 分类过滤 + 时间排序
CREATE INDEX idx_skills_user_id ON skills(user_id);
CREATE INDEX idx_skills_user_category ON skills(user_id, category);
CREATE INDEX idx_skills_user_updated ON skills(user_id, updated_at DESC);

-- Skill 步骤表：每个 Skill 包含有序的步骤列表
CREATE TABLE skill_steps (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    skill_id        UUID NOT NULL REFERENCES skills(id) ON DELETE CASCADE,
    step_order      INTEGER NOT NULL,
    title           VARCHAR(300) NOT NULL,
    description     TEXT,
    step_type       VARCHAR(20) NOT NULL DEFAULT 'action',
    code_template   TEXT,
    expected_output TEXT,
    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 索引：按 skillId 查询步骤并排序
CREATE INDEX idx_skill_steps_skill_id ON skill_steps(skill_id, step_order);

-- Skill 推荐表：AI/规则引擎生成的推荐，用户可采纳或忽略
CREATE TABLE skill_suggestions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name                VARCHAR(200) NOT NULL,
    description         TEXT,
    trigger_description TEXT,
    category            VARCHAR(50),
    suggested_steps     JSONB NOT NULL DEFAULT '[]',
    source_summary      TEXT,
    status              VARCHAR(20) NOT NULL DEFAULT 'pending',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 索引：按用户 + 状态查询推荐
CREATE INDEX idx_skill_suggestions_user ON skill_suggestions(user_id, status);

-- 用户行为记录表：为推荐引擎提供数据基础
CREATE TABLE user_activities (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type         VARCHAR(30) NOT NULL,
    framework    VARCHAR(50),
    keywords     TEXT[] DEFAULT '{}',
    language     VARCHAR(50),
    result_count INTEGER,
    metadata     JSONB,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 索引：按用户 + 时间倒序查询 + 按行为类型查询
CREATE INDEX idx_user_activities_user_time ON user_activities(user_id, created_at DESC);
CREATE INDEX idx_user_activities_type ON user_activities(user_id, type);
