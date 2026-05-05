-- 框架表
CREATE TABLE frameworks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL UNIQUE,           -- 框架名称，如 React
    slug VARCHAR(100) NOT NULL UNIQUE,            -- URL 友好标识，如 react
    base_url VARCHAR(500) NOT NULL,               -- 官方文档地址
    icon_url VARCHAR(500),                        -- 图标地址
    description TEXT,                             -- 框架简介
    category VARCHAR(50) NOT NULL,                -- frontend / backend / mobile
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_frameworks_slug ON frameworks (slug);

-- 知识链接表
CREATE TABLE knowledge_links (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    framework_id UUID NOT NULL REFERENCES frameworks(id) ON DELETE CASCADE,
    title VARCHAR(300) NOT NULL,                  -- 链接标题
    url VARCHAR(1000) NOT NULL,                   -- 文档 URL
    anchor VARCHAR(200),                          -- 锚点，用于深链接跳转
    description TEXT,                             -- 链接描述
    tags TEXT[] NOT NULL DEFAULT '{}',            -- 标签数组
    popularity_score INTEGER NOT NULL DEFAULT 0,  -- 热度分
    search_vector tsvector,                       -- 全文搜索向量（trigger 维护）
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_knowledge_links_framework_id ON knowledge_links (framework_id);
CREATE INDEX idx_knowledge_links_search ON knowledge_links USING GIN (search_vector);

-- 自动更新 search_vector 的 trigger 函数
CREATE OR REPLACE FUNCTION knowledge_links_search_vector_trigger() RETURNS trigger AS $$
BEGIN
    NEW.search_vector := to_tsvector('english',
        coalesce(NEW.title, '') || ' ' ||
        coalesce(NEW.description, '') || ' ' ||
        array_to_string(NEW.tags, ' ')
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- INSERT 和 UPDATE 时自动更新 search_vector
CREATE TRIGGER trg_knowledge_links_search_vector
    BEFORE INSERT OR UPDATE ON knowledge_links
    FOR EACH ROW EXECUTE FUNCTION knowledge_links_search_vector_trigger();
